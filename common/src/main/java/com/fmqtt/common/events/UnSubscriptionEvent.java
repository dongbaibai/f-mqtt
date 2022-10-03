package com.fmqtt.common.events;

public class UnSubscriptionEvent {

    private String topic;

    private boolean cleanSession;
    private String username;

    private UnSubscriptionEvent(String topic, boolean cleanSession, String username) {
        this.topic = topic;
        this.cleanSession = cleanSession;
        this.username = username;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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

    public static UnSubscriptionEvent buildEvent(String topicFilter, boolean cleanSession, String username) {
        return new UnSubscriptionEvent(topicFilter, cleanSession, username);
    }

}
