package com.fmqtt.queue;

import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.common.message.QueueMessage;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface QueueService extends Lifecycle {

    boolean addQueue(QueueMessage queueMessage);

    boolean removeInflight(int messageId, String clientId);

    boolean cleanQueue(String clientId);

    CompletableFuture<Collection<QueueMessage>> selfAdjusting(String clientId);

    int queueSize(String clientId);

    int inflightSize(String clientId);

}