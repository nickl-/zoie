package com.linkedin.zoie.api;

public interface DocIDMapperFactory {
  DocIDMapper<?> getDocIDMapper(ZoieMultiReader<?> reader);
}
