package com.fmqtt.metric.dropwizard;

import com.fmqtt.metric.metrics.Metric;

public interface Gauge<T> extends Metric {

    T value();

}
