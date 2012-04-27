package com.linkedin.zoie.hourglass.impl;

import com.linkedin.zoie.api.DocIDMapper;
import com.linkedin.zoie.api.DocIDMapperFactory;
import com.linkedin.zoie.api.ZoieIndexReader;
import com.linkedin.zoie.api.ZoieMultiReader;

public class NullDocIDMapperFactory implements DocIDMapperFactory
{
  public static final NullDocIDMapperFactory INSTANCE = new NullDocIDMapperFactory();
  public DocIDMapper<Object> getDocIDMapper(ZoieMultiReader<?> reader)
  {
    for(ZoieIndexReader<?>r : reader.getSequentialSubReaders())
    {
      r.setDocIDMapper(NullDocIDMapper.INSTANCE);
    }
    return NullDocIDMapper.INSTANCE;
  }  
}
