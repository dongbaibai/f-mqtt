package com.fmqtt.metric.dropwizard;

import com.fmqtt.common.config.BrokerConfig;

public class DropwizardGauge<T> implements Gauge<T> {

    private com.codahale.metrics.Gauge<T> gauge;

    private final String topic;

    public DropwizardGauge(com.codahale.metrics.Gauge<T> gauge,
                             String topic) {
        this.gauge = gauge;
        this.topic = topic;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public String serverName() {
        return BrokerConfig.serverName;
    }

    @Override
    public T value() {
        return gauge.getValue();
    }

    @Override
    public String jsonString() {
        return "{\"serverName\":" + serverName() + ", \"value\":" + value() + "}";
    }
}
