package com.linkedin.zoie.impl.indexing;

import org.apache.lucene.index.IndexReader;

import com.linkedin.zoie.api.IndexReaderFactory;
import com.linkedin.zoie.api.ZoieIndexReader;

public interface ReaderCacheFactory
{
  public <R extends IndexReader> AbstractReaderCache<R> newInstance(IndexReaderFactory<ZoieIndexReader<R>> readerfactory);
}
