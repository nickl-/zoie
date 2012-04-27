package com.linkedin.zoie.impl.indexing;

import java.util.List;

import org.apache.lucene.index.IndexReader;

import com.linkedin.zoie.api.ZoieException;
import com.linkedin.zoie.api.ZoieIndexReader;

public abstract class AbstractReaderCache<R extends IndexReader>
{
  public abstract List<ZoieIndexReader<R>> getIndexReaders();

  public abstract void returnIndexReaders(List<ZoieIndexReader<R>> readers);

  public abstract void refreshCache(long timeout) throws ZoieException;

  public abstract void start();

  public abstract void shutdown();

  public abstract void setFreshness(long freshness);

  public abstract long getFreshness();
  
}
