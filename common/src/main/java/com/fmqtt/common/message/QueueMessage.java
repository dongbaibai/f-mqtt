package com.fmqtt.common.message;

import java.io.Serializable;
import java.util.Arrays;

public class QueueMessage implements Serializable {

    private static final long serialVersionUID = 8197540895181442887L;

    private Integer messageId;
    private int qos;
    private String topic;
    private byte[] payload;
    private String senderId;
    private String targetId;
    private long createTs;

    public QueueMessage() {
    }

    public QueueMessage(int messageId, int qos, String topic, byte[] payload, String senderId, String targetId) {
        this.messageId = messageId;
        this.qos = qos;
        this.topic = topic;
        this.payload = payload;
        this.senderId = senderId;
        this.targetId = targetId;
        this.createTs = System.currentTimeMillis();
    }

    public boolean isExpired(long timeout) {
        return System.currentTimeMillis() - createTs > timeout;
    }

    public void reset() {
        createTs = System.currentTimeMillis();
    }


    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
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

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetId() {
        return targetId;
    }

    public long getCreateTs() {
        return createTs;
    }

    public void setCreateTs(long createTs) {
        this.createTs = createTs;
    }

    @Override
    public String toString() {
        return "QueueMessage{" +
                "messageId=" + messageId +
                ", qos=" + qos +
                ", topic='" + topic + '\'' +
                ", payload=" + Arrays.toString(payload) +
                ", senderId='" + senderId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", createTs=" + createTs +
                '}';
    }
}
