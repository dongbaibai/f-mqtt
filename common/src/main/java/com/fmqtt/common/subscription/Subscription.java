package com.fmqtt.common.subscription;

public class Subscription {

    private String topicFilter;
    private int qos;

    public Subscription() {

    }

    public Subscription(String topicFilter, int qos) {
        this.topicFilter = topicFilter;
        this.qos = qos;
    }

    public String getTopicFilter() {
        return topicFilter;
    }

    public void setTopicFilter(String topicFilter) {
        this.topicFilter = topicFilter;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

}
