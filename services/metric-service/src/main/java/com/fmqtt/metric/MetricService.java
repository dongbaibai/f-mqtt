package com.fmqtt.metric;

import com.codahale.metrics.MetricRegistry;
import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.metric.dropwizard.DropwizardMetricService;
import com.fmqtt.metric.dropwizard.Gauge;
import com.fmqtt.metric.metrics.Counter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface MetricService extends Lifecycle {

    Counter counter(String name, String topic);

    <T> Gauge<T> gauge(String name, String topic, Supplier<T> supplier);

    static MetricService dropwizardMetricService(Consumer<Serializable> metricConsumer) {
        return new DropwizardMetricService(new MetricRegistry(), metricConsumer);
    }

}
