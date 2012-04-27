package com.linkedin.zoie.impl.indexing.luceneNRT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

import com.linkedin.zoie.api.IndexReaderFactory;
import com.linkedin.zoie.api.LifeCycleCotrolledDataConsumer;
import com.linkedin.zoie.api.ZoieException;
import com.linkedin.zoie.api.indexing.ZoieIndexable;
import com.linkedin.zoie.api.indexing.ZoieIndexable.IndexingReq;
import com.linkedin.zoie.api.indexing.ZoieIndexableInterpreter;

public class ThrottledLuceneNRTDataConsumer<D> implements LifeCycleCotrolledDataConsumer<D>,IndexReaderFactory<IndexReader>
{
	private static final Logger logger = Logger.getLogger(ThrottledLuceneNRTDataConsumer.class);

	private static int MAX_READER_GENERATION = 3;
	/**
	 * document ID field name
	*/
	public static final String DOCUMENT_ID_FIELD = "id";
	  
	
	private IndexWriter _writer;
	private Analyzer _analyzer;
	private ZoieIndexableInterpreter<D> _interpreter;
	private Directory _dir;
	private final long _throttleFactor;
	private IndexReader _currentReader;
	private ReopenThread _reopenThread;
	private HashSet<IndexReader> _returnSet = new HashSet<IndexReader>();
	private ConcurrentLinkedQueue<IndexReader> _returnList = new ConcurrentLinkedQueue<IndexReader>();
	private final MergePolicy _mergePolicy;
	private boolean _appendOnly = false;
	private volatile String _version = null;
	
	public ThrottledLuceneNRTDataConsumer(Directory dir,Analyzer analyzer,ZoieIndexableInterpreter<D> interpreter,long throttleFactor,MergePolicy mergePolicy){
		_writer = null;
		_analyzer = analyzer;
		_interpreter = interpreter;
		_dir = dir;
		_throttleFactor = throttleFactor;
		_mergePolicy = mergePolicy;
		_currentReader = null;
		if (_throttleFactor<=0) throw new IllegalArgumentException("throttle factor must be > 0");
		_reopenThread = new ReopenThread();
	}
	
	
	public boolean isAppendOnly() {
		return _appendOnly;
	}


	public void setAppendOnly(boolean _appendOnly) {
		this._appendOnly = _appendOnly;
	}


	@Override
	public void start(){
		try {
			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_34,_analyzer);
			if (_mergePolicy!=null){
			  config.setMergePolicy(_mergePolicy);
			}
			_writer = new IndexWriter(_dir, config);
			_reopenThread.start();
		} catch (IOException e) {
			logger.error("uanble to start consumer: "+e.getMessage(),e);
		}
	}
	

	@Override
	public void stop(){
		_reopenThread.terminate();
		if (_currentReader!=null){
			try {
				_currentReader.close();
			} catch (IOException e) {
				logger.error(e.getMessage(),e);
			}
		}
		if (_writer!=null){
			try {
				_writer.close();
			} catch (IOException e) {
				logger.error(e.getMessage(),e);
			}
		}
	}
	
	public void consume(Collection<com.linkedin.zoie.api.DataConsumer.DataEvent<D>> events)
			throws ZoieException {
		if (_writer == null){
			throw new ZoieException("Internal IndexWriter null, perhaps not started?");
		}
		
		if (events.size() > 0){
			for (DataEvent<D> event : events){
				_version = event.getVersion();
				ZoieIndexable indexable = _interpreter.convertAndInterpret(event.getData());
				if (indexable.isSkip()) continue;
				if (!_appendOnly){
				  try {
				    _writer.deleteDocuments(new Term(DOCUMENT_ID_FIELD,String.valueOf(indexable.getUID())));
				  } catch(IOException e) {
				    throw new ZoieException(e.getMessage(),e);
				  }
				}
				  
			  IndexingReq[] reqs = indexable.buildIndexingReqs();
			  for (IndexingReq req : reqs){
				Analyzer localAnalyzer = req.getAnalyzer();
				Document doc = req.getDocument();
				Field uidField = new Field(DOCUMENT_ID_FIELD,String.valueOf(indexable.getUID()),Store.NO,Index.NOT_ANALYZED_NO_NORMS);
				uidField.setOmitNorms(true);
				doc.add(uidField);
				if (localAnalyzer == null) localAnalyzer = _analyzer;
				try {
					_writer.addDocument(doc, localAnalyzer);
				} catch(IOException e) {
					throw new ZoieException(e.getMessage(),e);
				}
			  }
			}
		}
	}

	public Analyzer getAnalyzer() {
		return _analyzer;
	}

	public IndexReader getDiskIndexReader() throws IOException {
		return _currentReader;
	}
	
	private volatile String _currentReaderVersion = null;

	@Override
	public String getCurrentReaderVersion() {
		return _currentReaderVersion;
	}

	public List<IndexReader> getIndexReaders() throws IOException {
		IndexReader subReader = getDiskIndexReader();
		ArrayList<IndexReader> list = new ArrayList<IndexReader>();
		if (subReader!=null){
			list.add(subReader);
		}
		return list;
	}

	public void returnIndexReaders(List<IndexReader> readers) {
		if (readers!=null){
			for (IndexReader r : readers){
				if (r != _currentReader){
					returnReader(r);
				}
			}
		}
	}
	
	private void returnReader(IndexReader reader){
		synchronized(_returnSet){
			if (!_returnSet.contains(reader)){
				_returnSet.add(reader);
				_returnList.add(reader);
			}
			while (_returnList.size()>=MAX_READER_GENERATION){
				logger.info("remove and close old reader: "+_returnList.size()+"/"+_returnSet.size());
				IndexReader r = _returnList.remove();
				_returnSet.remove(r);
				try {
					r.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	private class ReopenThread extends Thread{
		private volatile boolean _stop;
		ReopenThread(){
			super("reopen thread");
			setDaemon(true);
			_stop=false;
		}
		
		void terminate(){
			if (!_stop){
				_stop=true;
				interrupt();
			}
		}
		
		public void run(){
			while(!_stop){
			  synchronized(this){
				  try {
					  this.wait(ThrottledLuceneNRTDataConsumer.this._throttleFactor);
				  } catch (InterruptedException e) {
					  continue;
				  }
			  }
				if (ThrottledLuceneNRTDataConsumer.this._writer!=null){
					try {
						logger.info("updating reader...");
						IndexReader oldReader = ThrottledLuceneNRTDataConsumer.this._currentReader;
						ThrottledLuceneNRTDataConsumer.this._currentReader=IndexReader.open(ThrottledLuceneNRTDataConsumer.this._writer, true);
						_currentReaderVersion = _version;
						if (oldReader!=null){
							returnReader(oldReader);
						}
					} catch (IOException e) {
						logger.error(e.getMessage(),e);
					}
				}
			}
		}
	}
  
  public String getVersion()
  {
    return _version;
  }

	public Comparator<String> getVersionComparator()
  {
    throw new UnsupportedOperationException();
  }
}
