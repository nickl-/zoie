package com.linkedin.zoie.impl.indexing.internal;

import org.apache.lucene.index.IndexReader;

import com.linkedin.zoie.api.indexing.IndexReaderDecorator;

/**
 * @author "Xiaoyang Gu<xgu@linkedin.com>"
 * 
 */
public abstract class RAMIndexFactory<R extends IndexReader>
{
  public abstract RAMSearchIndex<R> newInstance(String version, IndexReaderDecorator<R> decorator, SearchIndexManager<R> idxMgr);
}
