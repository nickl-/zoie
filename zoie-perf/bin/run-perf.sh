#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

cd $bin/..
mvn -e exec:java -Dexec.mainClass=com.linkedin.zoie.perf.client.ZoiePerf
