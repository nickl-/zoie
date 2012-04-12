package com.linkedin.zoie.hourglass.impl;

import org.apache.lucene.index.IndexReader;

import com.linkedin.zoie.api.Zoie;
import com.linkedin.zoie.api.ZoieIndexReader;

public interface HourglassListener<R extends IndexReader, D> {
  public void onNewZoie(Zoie<R, D> zoie);
  public void onRetiredZoie(Zoie<R, D> zoie);
  public void onIndexReaderCleanUp(ZoieIndexReader<R> indexReader);
}
