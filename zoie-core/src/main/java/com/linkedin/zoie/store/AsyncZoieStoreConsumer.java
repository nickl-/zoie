package com.linkedin.zoie.store;

import java.util.Comparator;

import com.linkedin.zoie.impl.indexing.AsyncDataConsumer;

public class AsyncZoieStoreConsumer<D> extends AsyncDataConsumer<D> {

	public AsyncZoieStoreConsumer(Comparator<String> versionComparator) {
		super(versionComparator);
	}
}
