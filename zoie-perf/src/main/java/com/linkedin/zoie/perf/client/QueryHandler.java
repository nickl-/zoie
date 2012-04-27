package com.linkedin.zoie.perf.client;

public interface QueryHandler<T> {
	T handleQuery() throws Exception;
	String getCurrentVersion();
}
