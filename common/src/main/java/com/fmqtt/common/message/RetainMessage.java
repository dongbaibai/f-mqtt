package com.fmqtt.common.message;

import java.io.Serializable;
import java.util.Arrays;

public class RetainMessage implements Serializable {

    private static final long serialVersionUID = -3052954010244361129L;
    private int qos;
    private String topic;
    private byte[] payload;
    private String senderId;
    private long createTs;

    public RetainMessage() {
    }

    public RetainMessage(int qos, String topic, byte[] payload, String senderId) {
        this.qos = qos;
        this.topic = topic;
        this.payload = payload;
        this.senderId = senderId;
        this.createTs = System.currentTimeMillis();
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public long getCreateTs() {
        return createTs;
    }

    public void setCreateTs(long createTs) {
        this.createTs = createTs;
    }

    @Override
    public String toString() {
        return "RetainMessage{" +
                "qos=" + qos +
                ", topic='" + topic + '\'' +
                ", payload=" + Arrays.toString(payload) +
                ", senderId='" + senderId + '\'' +
                ", createTs=" + createTs +
                '}';
    }
}
