package com.fmqtt.metric.metrics;

public abstract class AbstractMetric implements Metric {

    private final String topic;

    public AbstractMetric(String topic) {
        this.topic = topic;
    }

    @Override
    public String topic() {
        return topic;
    }
}
