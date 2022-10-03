package com.fmqtt.session;

import com.fmqtt.cluster.ClusterService;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.events.Event;
import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.common.message.Will;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.event.EventService;
import com.fmqtt.metric.MetricService;
import com.fmqtt.plugin.PluginService;
import com.fmqtt.queue.QueueService;
import com.fmqtt.retain.RetainService;
import com.fmqtt.subscription.SubscriptionService;
import com.fmqtt.thirdpart.version.VersionService;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface SessionService extends Lifecycle {

    //// mqtt相关
    CompletableFuture<Session> openSession(String clientId, String username,
                                           boolean cleanSession, Will will);

    CompletableFuture<Boolean> closeSession(String clientId, String username, DisconnectType type);

    CompletableFuture<Boolean> subscribe(Subscription subscription, String clientId,
                                         boolean cleanSession, String username);

    CompletableFuture<Boolean> unsubscribe(String topics, String clientId,
                                           boolean cleanSession, String username);

    CompletableFuture<Void> handlePublish(String clientId, String topic, byte[] payload,
                                          int messageId, int qos, boolean dup, boolean retain);
    boolean removeInflight(int messageId, String clientId);

    //// services相关
    void setSubscriptionService(SubscriptionService subscriptionService);

    void setRetainService(RetainService retainService);

    void setQueueService(QueueService queueService);

    void setClusterService(ClusterService clusterService);

    void setEventService(EventService eventService);

    void setMetricService(MetricService metricService);

    void setVersionService(VersionService versionService);

    void setPluginService(PluginService pluginService);

    //// consumer相关
    Consumer<Serializable> messageConsumer();

    Consumer<Event> connectEventSyncConsumer();

    Consumer<Event> disconnectEventSyncConsumer();

    Consumer<Event> subscribeEventSyncConsumer();

    Consumer<Event> unsubscribeEventSyncConsumer();

    Consumer<Event> connectEventPersistConsumer();

    Consumer<Event> disconnectEventPersistConsumer();

    Consumer<Event> subscribeEventPersistConsumer();

    Consumer<Event> unsubscribeEventPersistConsumer();

    Consumer<Event> addRetainEventPersistConsumer();

    Consumer<Event> addQueueEventPersistConsumer();

    Consumer<Event> removeQueueEventPersistConsumer();

    Consumer<Event> cleanQueueEventPersistConsumer();

}