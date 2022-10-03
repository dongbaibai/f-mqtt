package com.fmqtt.queue;

import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.message.QueueMessage;
import com.fmqtt.common.util.ExecutorServiceUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public abstract class AbstractQueueService extends AbstractLifecycle implements QueueService {

    private ExecutorService executorService;
    abstract List<QueueMessage> adjust(String clientId);

    protected abstract boolean saveQueue(QueueMessage queueMessage);
    protected abstract boolean deleteQueue(int messageId, String clientId);
    protected abstract boolean deleteAllQueue(String clientId);

    public AbstractQueueService() {
        this.executorService = ExecutorServiceUtils.defaultExecutorService("queue-service-thread");
    }

    @Override
    public boolean addQueue(QueueMessage queueMessage) {
        if (!isRunning()) {
            return false;
        }
        return saveQueue(queueMessage);
    }


    @Override
    public boolean removeInflight(int messageId, String clientId) {
        if (!isRunning()) {
            return false;
        }

        return deleteQueue(messageId, clientId);
    }

    @Override
    public boolean cleanQueue(String clientId) {
        if (!isRunning()) {
            return false;
        }

        return deleteAllQueue(clientId);
    }


    /**
     * move message from queue to inflight and send
     */
    public CompletableFuture<Collection<QueueMessage>> selfAdjusting(String clientId) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> adjust(clientId), executorService);

    }

}
