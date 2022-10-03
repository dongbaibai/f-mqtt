package com.fmqtt.event;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.events.Event;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.event.kafka.KafkaEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public abstract class AbstractEventService extends AbstractLifecycle implements EventService {

    private final static Logger log = LoggerFactory.getLogger(KafkaEventService.class);
    // 内存同步消费者回调
    private Consumer<Event> connectEventSyncConsumer;
    private Consumer<Event> disconnectEventSyncConsumer;
    private Consumer<Event> subscribeEventSyncConsumer;
    private Consumer<Event> unsubscribeEventSyncConsumer;
    // 持久化消费者回调
    private Consumer<Event> connectEventPersistConsumer;
    private Consumer<Event> disconnectEventPersistConsumer;
    private Consumer<Event> subscribeEventPersistConsumer;
    private Consumer<Event> unsubscribeEventPersistConsumer;
    private Consumer<Event> addRetainEventPersistConsumer;
    private Consumer<Event> addQueueEventPersistConsumer;
    private Consumer<Event> removeQueueEventPersistConsumer;
    private Consumer<Event> cleanQueueEventPersistConsumer;

    @Override
    public void start() throws Exception {
        AssertUtils.isTrue(connectEventSyncConsumer != null
                , "connectEventConsumer MUST be set");
        AssertUtils.isTrue(disconnectEventSyncConsumer != null
                , "disconnectEventConsumer MUST be set");
        AssertUtils.isTrue(subscribeEventSyncConsumer != null
                , "subscribeEventConsumer MUST be set");
        AssertUtils.isTrue(unsubscribeEventSyncConsumer != null
                , "unsubscribeEventConsumer MUST be set");

        AssertUtils.isTrue(connectEventPersistConsumer != null
                , "connectEventPersistConsumer MUST be set");
        AssertUtils.isTrue(disconnectEventPersistConsumer != null
                , "disconnectEventPersistConsumer MUST be set");
        AssertUtils.isTrue(subscribeEventPersistConsumer != null
                , "subscribeEventPersistConsumer MUST be set");
        AssertUtils.isTrue(unsubscribeEventPersistConsumer != null
                , "unsubscribeEventPersistConsumer MUST be set");
        AssertUtils.isTrue(addRetainEventPersistConsumer != null
                , "addRetainEventPersistConsumer MUST be set");
        AssertUtils.isTrue(addQueueEventPersistConsumer != null
                , "addQueueEventPersistConsumer MUST be set");
        AssertUtils.isTrue(removeQueueEventPersistConsumer != null
                , "removeQueueEventPersistConsumer MUST be set");
        AssertUtils.isTrue(cleanQueueEventPersistConsumer != null
                , "cleanQueueEventPersistConsumer MUST be set");
        super.start();
    }

    // 这里要多线程来处理哈
    protected final void handleSyncEvent(Event event) {

        if (!isRunning()
                // 过滤掉自己发送的数据,这里是广播
                || BrokerConfig.serverName.equals(event.getSender())) {
            return;
        }
        log.info("HandleSyncEvent receive event:[{}]", event);
        switch (event.getType()) {
            case CONNECT:
                connectEventSyncConsumer.accept(event);
                break;
            case DISCONNECT:
                disconnectEventSyncConsumer.accept(event);
                break;
            case SUBSCRIBE:
                subscribeEventSyncConsumer.accept(event);
                break;
            case UNSUBSCRIBE:
                unsubscribeEventSyncConsumer.accept(event);
                break;
            default:
                log.error("Unknown eventType:[{}]", event.getType());
        }
    }

    protected final void handlePersistEvent(Event event) {
        if (!isRunning()) {
            return;
        }
        log.info("HandlePersistEvent receive event:[{}]", event);
        switch (event.getType()) {
            case CONNECT:
                connectEventPersistConsumer.accept(event);
                break;
            case DISCONNECT:
                disconnectEventPersistConsumer.accept(event);
                break;
            case SUBSCRIBE:
                subscribeEventPersistConsumer.accept(event);
                break;
            case UNSUBSCRIBE:
                unsubscribeEventPersistConsumer.accept(event);
                break;
            case ADD_RETAIN:
                addRetainEventPersistConsumer.accept(event);
                break;
            case ADD_QUEUE:
                addQueueEventPersistConsumer.accept(event);
                break;
            case REMOVE_QUEUE:
                removeQueueEventPersistConsumer.accept(event);
                break;
            case CLEAN_QUEUE:
                cleanQueueEventPersistConsumer.accept(event);
                break;
            default:
                log.error("Unknown eventType:[{}]", event.getType());
        }
    }

    @Override
    public void setConnectEventSyncConsumer(Consumer<Event> consumer) {
        this.connectEventSyncConsumer = consumer;
    }

    @Override
    public void setDisconnectEventSyncConsumer(Consumer<Event> consumer) {
        this.disconnectEventSyncConsumer = consumer;
    }

    @Override
    public void setSubscribeEventSyncConsumer(Consumer<Event> consumer) {
        this.subscribeEventSyncConsumer = consumer;
    }

    @Override
    public void setUnsubscribeEventSyncConsumer(Consumer<Event> consumer) {
        this.unsubscribeEventSyncConsumer = consumer;
    }

    @Override
    public void setConnectEventPersistConsumer(Consumer<Event> connectEventPersistConsumer) {
        this.connectEventPersistConsumer = connectEventPersistConsumer;
    }

    @Override
    public void setDisconnectEventPersistConsumer(Consumer<Event> disconnectEventPersistConsumer) {
        this.disconnectEventPersistConsumer = disconnectEventPersistConsumer;
    }

    @Override
    public void setSubscribeEventPersistConsumer(Consumer<Event> subscribeEventPersistConsumer) {
        this.subscribeEventPersistConsumer = subscribeEventPersistConsumer;
    }

    @Override
    public void setUnsubscribeEventPersistConsumer(Consumer<Event> unsubscribeEventPersistConsumer) {
        this.unsubscribeEventPersistConsumer = unsubscribeEventPersistConsumer;
    }

    @Override
    public void setAddRetainEventPersistConsumer(Consumer<Event> addRetainEventPersistConsumer) {
        this.addRetainEventPersistConsumer = addRetainEventPersistConsumer;
    }

    @Override
    public void setAddQueueEventPersistConsumer(Consumer<Event> addQueueEventPersistConsumer) {
        this.addQueueEventPersistConsumer = addQueueEventPersistConsumer;
    }

    @Override
    public void setRemoveQueueEventPersistConsumer(Consumer<Event> removeQueueEventPersistConsumer) {
        this.removeQueueEventPersistConsumer = removeQueueEventPersistConsumer;
    }

    @Override
    public void setCleanQueueEventPersistConsumer(Consumer<Event> cleanQueueEventPersistConsumer) {
        this.cleanQueueEventPersistConsumer = cleanQueueEventPersistConsumer;
    }

}
