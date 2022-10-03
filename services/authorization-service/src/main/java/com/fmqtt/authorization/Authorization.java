package com.fmqtt.authorization;

public class Authorization {

    private String topic;
    private Action action;

    public Authorization(String topic, Action action) {
        this.topic = topic;
        this.action = action;
    }

    public Authorization(String topic) {
        this.topic = topic;
        this.action = Action.READWRITE;
    }

    public Action getAction() {
        return action;
    }

    public String getTopic() {
        return topic;
    }

}
