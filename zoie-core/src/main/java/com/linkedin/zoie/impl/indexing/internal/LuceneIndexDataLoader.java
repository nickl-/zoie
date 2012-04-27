package com.linkedin.zoie.impl.indexing.internal;
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Similarity;

import com.linkedin.zoie.api.DataConsumer;
import com.linkedin.zoie.api.ZoieException;
import com.linkedin.zoie.api.ZoieHealth;
import com.linkedin.zoie.api.ZoieIndexReader;
import com.linkedin.zoie.api.ZoieSegmentReader;
import com.linkedin.zoie.api.indexing.AbstractZoieIndexable;
import com.linkedin.zoie.api.indexing.IndexingEventListener;
import com.linkedin.zoie.api.indexing.ZoieIndexable;
import com.linkedin.zoie.api.indexing.ZoieIndexable.IndexingReq;

public abstract class LuceneIndexDataLoader<R extends IndexReader> implements DataConsumer<ZoieIndexable>
{
	private static final Logger log = Logger.getLogger(LuceneIndexDataLoader.class);
	protected final Analyzer _analyzer;
	protected final Similarity _similarity;
	protected final SearchIndexManager<R> _idxMgr;
	protected final Comparator<String> _versionComparator;
	private Filter _purgeFilter;

  private final Queue<IndexingEventListener> _lsnrList;

	protected LuceneIndexDataLoader(Analyzer analyzer, Similarity similarity,SearchIndexManager<R> idxMgr,Comparator<String> versionComparator,Queue<IndexingEventListener> lsnrList) {
		_analyzer = analyzer;
		_similarity = similarity;
		_idxMgr=idxMgr;
		_versionComparator = versionComparator;
		_purgeFilter = null;
		_lsnrList = lsnrList;
	}
	
	public void setPurgeFilter(Filter purgeFilter){
		_purgeFilter = purgeFilter;
	}

	protected abstract BaseSearchIndex<R> getSearchIndex();
	
    protected abstract void propagateDeletes(LongSet delDocs) throws IOException;
    protected abstract void commitPropagatedDeletes() throws IOException;
    
    private final void purgeDocuments(){
    	if (_purgeFilter!=null){
    		BaseSearchIndex<R> idx = getSearchIndex();
    		IndexReader writeReader = null;
    		log.info("purging docs started...");
    		int count = 0;
    		long start = System.currentTimeMillis();

        ZoieIndexReader<R> reader = null;
    		try{
          synchronized(idx)
          {
            reader = idx.openIndexReader();
            if (reader != null)
              reader.incZoieRef();
          }

    		  writeReader = idx.openIndexReaderForDelete();

    			DocIdSetIterator iter = _purgeFilter.getDocIdSet(reader).iterator();
    			
    			int doc;
    			while((doc = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
    				count++;
    				writeReader.deleteDocument(doc);
    			}
    		}
    		catch(Throwable th){
    			log.error("problem creating purge filter: "+th.getMessage(),th);
    		}
    		finally{
          if (reader != null)
            reader.decZoieRef();
    			if (writeReader!=null){
    				try{
    				  writeReader.close();
    				}
    				catch(IOException ioe){
    					ZoieHealth.setFatal();
    					log.error(ioe.getMessage(),ioe);
    				}
    			}
    		}
    		
    		long end = System.currentTimeMillis();
    		log.info("purging docs completed in "+(end-start)+"ms");
    		log.info("total docs purged: " +count);
    	}
    }

	/**
	 * @Precondition incoming events sorted by version number
	 * <br>every event in the events collection must be non-null
	 * 
	 * @see com.linkedin.zoie.api.DataConsumer#consume(java.util.Collection)
	 * 
	 */
	public void consume(Collection<DataEvent<ZoieIndexable>> events) throws ZoieException {
		
        if (events == null)
			return;

        int eventCount = events.size();
        if (eventCount==0){
        	return;
        }
		BaseSearchIndex<R> idx = getSearchIndex();

		if (idx==null){
			throw new ZoieException("trying to consume to null index");
		}
		Long2ObjectMap<List<IndexingReq>> addList = new Long2ObjectOpenHashMap<List<IndexingReq>>();
		String version = idx.getVersion();		// current version

		LongSet delSet =new LongOpenHashSet();
		
		try {
		  for(DataEvent<ZoieIndexable> evt : events)
		  {
		    if (evt == null) continue;
    		    //version = Math.max(version, evt.getVersion());
		        version = version == null ? evt.getVersion() : (_versionComparator.compare(version,evt.getVersion()) < 0 ? evt.getVersion() : version);
		        
		        if (evt instanceof MarkerDataEvent) continue;
    		    // interpret and get get the indexable instance
    		    ZoieIndexable indexable = evt.getData();
    		    if (indexable == null || indexable.isSkip())
    		      continue;
    
    		    long uid = indexable.getUID();
    		    delSet.add(uid);
    		    addList.remove(uid);
				if (!indexable.isDeleted()) // update event
				{
					IndexingReq[] reqs = indexable.buildIndexingReqs();
					for (IndexingReq req : reqs) {
						if (req != null) // if doc is provided, interpret as
											// a delete, e.g. update with
											// nothing
						{
							Document doc = req.getDocument();
							if (doc!=null){							 
							  ZoieSegmentReader.fillDocumentID(doc, uid);
							  if (indexable.isStorable()){
							    byte[] bytes = indexable.getStoreValue();
							    if (bytes!=null){
							      doc.add(new Field(AbstractZoieIndexable.DOCUMENT_STORE_FIELD,bytes));
							    }
							  }
							}
							// add to the insert list
							List<IndexingReq> docList = addList.get(uid);
							if (docList == null) {
								docList = new LinkedList<IndexingReq>();
								addList.put(uid, docList);
							}
							docList.add(req);
						}
					}
				}
				// hao: we do not need the following few lines
				//else {
					//addList.remove(uid);
				//}
			}

			List<IndexingReq> docList = new ArrayList<IndexingReq>(addList.size());
			for (List<IndexingReq> tmpList : addList.values()) {
				docList.addAll(tmpList);
			}

      purgeDocuments();
      idx.updateIndex(delSet, docList, _analyzer,_similarity);
      propagateDeletes(delSet);
			synchronized(_idxMgr)
			{
         idx.refresh();
         commitPropagatedDeletes();
			}
		} catch (IOException ioe) {
      ZoieHealth.setFatal();
			log.error("Problem indexing batch: " + ioe.getMessage(), ioe);
		} finally {
			try {
				if (idx != null) {
          idx.setVersion(version); // update the version of the
					idx.incrementEventCount(eventCount);
												// index
				}
			} catch (Exception e) // catch all exceptions, or it would screw
									// up jobs framework
			{
				log.warn(e.getMessage());
			} finally {
				if (idx instanceof DiskSearchIndex<?>) {
					log.info("disk indexing requests flushed.");
				}
			}
		}
	}
	
    public void loadFromIndex(RAMSearchIndex<R> ramIndex) throws ZoieException
    {
      try
      {
        // hao: get disk search idx, 
        BaseSearchIndex<R> idx = getSearchIndex();
        //hao: merge the realyOnly ram idx with the disk idx
        idx.loadFromIndex(ramIndex);
        idx.clearDeletes(); // clear old deletes as deletes are written to the lucene index
        // hao: update the disk idx reader
        idx.refresh(); // load the index reader
        purgeDocuments();
        idx.markDeletes(ramIndex.getDelDocs()); // inherit deletes
        idx.commitDeletes();
        idx.incrementEventCount(ramIndex.getEventsHandled());
        
        //Map<String, String> commitData = idx.getCommitData();
        //System.out.println("disk vesion from the commit data" + commitData);  
        
        //V newVersion = idx.getVersion().compareTo(ramIndex.getVersion()) < 0 ? ramIndex.getVersion(): idx.getVersion();
        String newVersion = idx.getVersion() == null ? ramIndex.getVersion() : (_versionComparator.compare(idx.getVersion(), ramIndex.getVersion()) < 0 ? ramIndex.getVersion(): idx.getVersion());
        idx.setVersion(newVersion);      
        //System.out.println("disk verson from the signature" + newVersion.toString());        
               
        //idx.setVersion(Math.max(idx.getVersion(), ramIndex.getVersion()));
      }
      catch(IOException ioe)
      {
        ZoieHealth.setFatal();
        log.error("Problem copying segments: " + ioe.getMessage(), ioe);
        throw new ZoieException(ioe);
      }
    }
 

  /**
   * @return the version number of the search index.
   */
  public String getVersion()
  {
    BaseSearchIndex<R> idx = getSearchIndex();
    String version = null;
    if (idx != null) version = idx.getVersion();
    return version;
  }

	/**
   * @return the version comparator.
   */
	public Comparator<String> getVersionComparator() {
    return _versionComparator;
  }
}
