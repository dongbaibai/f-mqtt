package com.fmqtt.cluster.akka;

import java.io.Serializable;

public class PublishMessage implements Serializable {

    private static final long serialVersionUID = -362809915730614362L;
    private String topic;
    private int qos;
    private String clientId;
    private int messageId;
    private boolean dup;
    private byte[] payload;
    // we need retain to storeMessage?
    // This depends on how to store retain message
    // This field is useless when global storage is used
    private boolean retain;
    // we need username to authorize?
    private String username;
    private String senderServerName;

    public PublishMessage(String topic, int qos, String clientId,
                          int messageId, boolean dup, byte[] payload, String senderServerName) {
        this.topic = topic;
        this.qos = qos;
        this.clientId = clientId;
        this.messageId = messageId;
        this.dup = dup;
        this.payload = payload;
        this.senderServerName = senderServerName;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isRetain() {
        return retain;
    }

    public void setRetain(boolean retain) {
        this.retain = retain;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public boolean isDup() {
        return dup;
    }

    public void setDup(boolean dup) {
        this.dup = dup;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSenderServerName() {
        return senderServerName;
    }

    public void setSenderServerName(String senderServerName) {
        this.senderServerName = senderServerName;
    }
}
