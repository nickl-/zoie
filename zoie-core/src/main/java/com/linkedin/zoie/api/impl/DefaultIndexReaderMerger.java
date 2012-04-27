package com.linkedin.zoie.api.impl;

import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;

import com.linkedin.zoie.api.IndexReaderMerger;
import com.linkedin.zoie.api.ZoieIndexReader;

public class DefaultIndexReaderMerger<T extends IndexReader> implements IndexReaderMerger<MultiReader, T> {
	public MultiReader mergeIndexReaders(List<ZoieIndexReader<T>> readerList) {
		MultiReader r = new MultiReader(readerList.toArray(new IndexReader[readerList.size()]),false);
		return r;
	}
}
