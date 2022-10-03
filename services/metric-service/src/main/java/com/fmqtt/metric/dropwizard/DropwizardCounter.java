package com.fmqtt.metric.dropwizard;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.metric.metrics.Counter;

public class DropwizardCounter implements Counter {

    private com.codahale.metrics.Counter counter;

    private final String topic;

    public DropwizardCounter(com.codahale.metrics.Counter counter,
                             String topic) {
        this.counter = counter;
        this.topic = topic;
    }

    @Override
    public void inc() {
        counter.inc();
    }

    @Override
    public void inc(long n) {
        counter.inc(n);
    }

    @Override
    public void dec() {
        counter.dec();
    }

    @Override
    public void dec(long n) {
        counter.dec(n);
    }

    @Override
    public double value() {
        return counter.getCount();
    }

    @Override
    public String jsonString() {
        return "{\"serverName\":" + serverName() + ", \"value\":" + value() + "}";
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public String serverName() {
        return BrokerConfig.serverName;
    }

}
