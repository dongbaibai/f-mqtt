package com.fmqtt.metric.metrics;

public interface Counter extends Metric {

    void inc();

    void inc(long n);

    void dec();

    void dec(long n);

    double value();

}
