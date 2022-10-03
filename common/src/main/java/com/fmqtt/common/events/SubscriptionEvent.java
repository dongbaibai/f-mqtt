package com.fmqtt.common.events;

import com.fmqtt.common.subscription.Subscription;

import javax.swing.plaf.synth.SynthTextAreaUI;

public class SubscriptionEvent {

    private String topicFilter;
    private int qos;
    private boolean cleanSession;
    private String username;

    private SubscriptionEvent(String topicFilter, int qos, boolean cleanSession, String username) {
        this.topicFilter = topicFilter;
        this.qos = qos;
        this.cleanSession = cleanSession;
        this.username = username;
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

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public static SubscriptionEvent buildEvent(String topicFilter, int qos, boolean cleanSession, String username) {
        return new SubscriptionEvent(topicFilter, qos, cleanSession, username);
    }

}
