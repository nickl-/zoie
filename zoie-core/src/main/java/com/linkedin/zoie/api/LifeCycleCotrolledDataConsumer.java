package com.linkedin.zoie.api;

public interface LifeCycleCotrolledDataConsumer<D> extends DataConsumer<D> {
  void start();
  void stop();
}
