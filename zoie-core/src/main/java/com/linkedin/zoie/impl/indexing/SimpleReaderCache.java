package com.linkedin.zoie.impl.indexing;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import com.linkedin.zoie.api.IndexReaderFactory;
import com.linkedin.zoie.api.ZoieException;
import com.linkedin.zoie.api.ZoieIndexReader;

public class SimpleReaderCache<R extends IndexReader> extends AbstractReaderCache<R>{

	private static final Logger logger = Logger.getLogger(SimpleReaderCache.class);
	
	private final IndexReaderFactory<ZoieIndexReader<R>> _readerFactory;
	
	public SimpleReaderCache(IndexReaderFactory<ZoieIndexReader<R>> readerfactory){
	  _readerFactory = readerfactory;
	}
	
	@Override
	public List<ZoieIndexReader<R>> getIndexReaders() {
	  try {
		return _readerFactory.getIndexReaders();
	  } catch (IOException e) {
		logger.error(e.getMessage(),e);
		return new ArrayList<ZoieIndexReader<R>>();
	  }
	}

	@Override
	public void returnIndexReaders(List<ZoieIndexReader<R>> readers) {
		_readerFactory.returnIndexReaders(readers);
	}

	@Override
	public void refreshCache(long timeout) throws ZoieException {
		
	}

	@Override
	public void start() {

	}

	@Override
	public void shutdown() {
		
	}

	@Override
	public void setFreshness(long freshness) {
		
	}

	@Override
	public long getFreshness() {
		return 0;
	}
	
	public static ReaderCacheFactory FACTORY = new ReaderCacheFactory(){

	@Override
	public <R extends IndexReader> AbstractReaderCache<R> newInstance(IndexReaderFactory<ZoieIndexReader<R>> readerfactory)
	{
	      return new SimpleReaderCache<R>(readerfactory);
	}};

}
