package com.fmqtt.common.events;

import com.fmqtt.common.config.BrokerConfig;

import java.util.Arrays;

public class Event {
    private long tick;
    private EventType type;
    private String clientId;
    private String sender;

    private byte[] body;
    private long createTs;

    public Event() {
    }

    public Event(EventType type, String clientId, long tick, byte[] body) {
        this.type = type;
        this.sender = BrokerConfig.serverName;
        this.clientId = clientId;
        this.tick = tick;
        this.body = body;
        this.createTs = System.currentTimeMillis();
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public long getCreateTs() {
        return createTs;
    }

    public void setCreateTs(long createTs) {
        this.createTs = createTs;
    }

    public long getTick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "Event{" +
                "tick=" + tick +
                ", type=" + type +
                ", clientId='" + clientId + '\'' +
                ", sender='" + sender + '\'' +
                ", createTs=" + createTs +
                '}';
    }
}
