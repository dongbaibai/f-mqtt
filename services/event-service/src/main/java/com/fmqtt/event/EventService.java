package com.fmqtt.event;

import com.fmqtt.common.events.Event;
import com.fmqtt.common.lifecycle.Lifecycle;

import java.util.function.Consumer;

public interface EventService extends Lifecycle {

    boolean publishConnect(String clientId, long tick, byte[] body);

    boolean publishDisconnect(String clientId, long tick, byte[] body);

    boolean publishSubscribe(String clientId, long tick, byte[] body);

    boolean publishUnsubscribe(String clientId, long tick, byte[] body);

    boolean publishAddRetain(String clientId, long tick, byte[] body);

    // TODO: 2022/9/21 这里是否需要删除
    boolean publishAddQueue(String clientId, long tick, byte[] body);

    boolean publishRemoveQueue(String clientId, long tick, byte[] body);

    boolean publishCleanQueue(String clientId, long tick, byte[] body);

    void setConnectEventSyncConsumer(Consumer<Event> consumer);

    void setDisconnectEventSyncConsumer(Consumer<Event> consumer);

    void setSubscribeEventSyncConsumer(Consumer<Event> consumer);

    void setUnsubscribeEventSyncConsumer(Consumer<Event> consumer);

    void setConnectEventPersistConsumer(Consumer<Event> connectEventPersistConsumer);

    void setDisconnectEventPersistConsumer(Consumer<Event> disconnectEventPersistConsumer);

    void setSubscribeEventPersistConsumer(Consumer<Event> subscribeEventPersistConsumer);

    void setUnsubscribeEventPersistConsumer(Consumer<Event> unsubscribeEventPersistConsumer);

    void setAddRetainEventPersistConsumer(Consumer<Event> addRetainEventPersistConsumer);

    void setAddQueueEventPersistConsumer(Consumer<Event> addQueueEventPersistConsumer);

    void setRemoveQueueEventPersistConsumer(Consumer<Event> removeQueueEventPersistConsumer);

    void setCleanQueueEventPersistConsumer(Consumer<Event> cleanQueueEventPersistConsumer);
}
