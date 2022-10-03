package com.fmqtt.session;

import com.alibaba.fastjson.JSON;
import com.fmqtt.cluster.ClusterService;
import com.fmqtt.cluster.akka.PublishMessage;
import com.fmqtt.common.channel.Channel;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.events.*;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.message.QueueMessage;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.common.message.Will;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.common.util.ExecutorServiceUtils;
import com.fmqtt.common.util.QoSUtils;
import com.fmqtt.event.EventService;
import com.fmqtt.metric.MetricService;
import com.fmqtt.metric.metrics.Metric;
import com.fmqtt.plugin.PluginService;
import com.fmqtt.queue.QueueService;
import com.fmqtt.retain.RetainService;
import com.fmqtt.subscription.MemorySubscriptionService;
import com.fmqtt.subscription.SubscriptionService;
import com.fmqtt.thirdpart.version.VersionService;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.fmqtt.common.constant.AllConstants.SYS_CLIENT_ID;

/**
 * 提供Session操作的模板方法,继承该类来实现Session的存储方式即可
 * 考虑到本地存储带来的问题,把H2MvStore的实现直接去掉了
 */
public abstract class AbstractSessionService extends AbstractLifecycle implements SessionService {

    private final static Logger log = LoggerFactory.getLogger(AbstractSessionService.class);

    protected MemorySubscriptionService memorySubscriptionService;
    protected final static Map</* clientId */String, Session> SESSIONS = Maps.newConcurrentMap();
    protected SubscriptionService subscriptionService;
    protected VersionService versionService;
    protected RetainService retainService;
    protected QueueService queueService;
    protected ClusterService clusterService;
    protected EventService eventService;
    protected MetricService metricService;
    protected PluginService pluginService;
    protected ExecutorService executorService;
    private final ExecutorService senderExecutorService;

    public AbstractSessionService() {
        this.executorService = ExecutorServiceUtils.defaultExecutorService("session-service-thread");
        this.senderExecutorService = ExecutorServiceUtils.defaultExecutorService("send-message-thread");
        this.memorySubscriptionService = MemorySubscriptionService.getInstance();
    }

    //---------- 具体子类实现存储
    protected abstract Map<String, Session> findAll();

    protected abstract boolean saveSession(Session session);

    @Override
    public void start() throws Exception {
        AssertUtils.isTrue(pluginService != null, "pluginService MUST be set");
        AssertUtils.isTrue(versionService != null, "versionService MUST be set");
        AssertUtils.isTrue(subscriptionService != null, "subscriptionService MUST be set");
        AssertUtils.isTrue(retainService != null, "retainService MUST be set");
        AssertUtils.isTrue(queueService != null, "queueService MUST be set");
        AssertUtils.isTrue(metricService != null, "metricService MUST be set");
        AssertUtils.isTrue(clusterService != null, "clusterService MUST be set");
        AssertUtils.isTrue(eventService != null, "eventService MUST be set");
        if (BrokerConfig.metricEnable) {
            metricService.gauge("client.connected", "$SYS/client/connected",
                    () -> SESSIONS.entrySet().stream().filter(entry ->
                            entry.getValue().localSession() && entry.getValue().connected()).count());
            metricService.gauge("client.disconnected", "$SYS/client/disconnected",
                    () -> SESSIONS.entrySet().stream().filter(entry ->
                            entry.getValue().localSession() && !entry.getValue().connected()).count());
            metricService.gauge("queue.count", "$SYS/queue/count", () -> {
                LongAdder sum = new LongAdder();
                SESSIONS.entrySet().stream()
                        .filter(entry -> entry.getValue().localSession())
                        .forEach(entry -> sum.add(queueService.queueSize(entry.getKey())));
                return sum.longValue();
            });
            metricService.gauge("inflight.count", "$SYS/inflight/count", () -> {
                LongAdder sum = new LongAdder();
                SESSIONS.entrySet().stream()
                        .filter(entry -> entry.getValue().localSession())
                        .forEach(entry -> sum.add(queueService.inflightSize(entry.getKey())));
                return sum.longValue();
            });
        }
        Map<String, Session> sessions = findAll();
        SESSIONS.putAll(sessions);
        log.info("Start to self adjusting for inflight");
        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("adjusting-inflight-thread-%s").build()
        ).scheduleWithFixedDelay(() -> {
            try {
                selfAdjusting();
            } catch (Throwable throwable) {
                log.error("SelfAdjusting error", throwable);
            }
        }, BrokerConfig.adjustingInflightIntervalMills, BrokerConfig.adjustingInflightIntervalMills, TimeUnit.MILLISECONDS);

        super.start();
    }

    private void selfAdjusting() {
        SESSIONS.values().forEach(s -> {
            if (s.localSession() && s.connected()) {
                queueService.selfAdjusting(s.getClientId())
                        .thenAcceptAsync(queueMessages -> {
                            if (CollectionUtils.isNotEmpty(queueMessages)) {
                                log.info("selfAdjusting for clientId:[{}], inflight:[{}]", s.getClientId(), queueMessages);
                                queueMessages.forEach(q ->
                                        s.sendMessage(q.getPayload(), q.getMessageId(), q.getTopic(), 1, false));
                            }
                        }, senderExecutorService);
            }
        });
    }

    @Override
    public CompletableFuture<Session> openSession(String clientId, String username,
                                                  boolean cleanSession, Will will) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(null);
        }

        // 这里即可将控制报文控制器对于的线程池释放出来
        return CompletableFuture.supplyAsync(() -> {
            long tick = versionService.tick(AllConstants.SESSION_VERSION_KEY, clientId);
            // versionService.tick(AllConstants.QUEUE_VERSION_KEY, clientId);
            ConnectionEvent event = ConnectionEvent.buildEvent(clientId, username, cleanSession, will);
            if (!eventService.publishConnect(clientId, tick, JSON.toJSONBytes(event))) {
                return null;
            }
            Session session = Session.buildSession(event);
            doOpenSession(session, tick, true);
            return session;
        }, executorService);
    }

    private void doOpenSession(Session session, long tick, boolean local) {
        // 校验版本号
        if (versionService.isExpired(AllConstants.SESSION_VERSION_KEY, session.getClientId(), tick)) {
            log.warn("Drop expired connect event for [sync], clientId:[{}], tick:[{}]", session.getClientId(), tick);
            return;
        }
        if (!local) {
            Channel channel = clusterService.buildChannel(BrokerConfig.serverName);
            session.setChannel(channel);
        }
        session.setSessionService(this);
        Session old = get(session.getClientId());
        if (old != null) {
            old.close(); // 断开老Session
            if (session.isCleanSession()) {
                cleanSession(old);
            } else {
                reconnect(session);
            }
        }
        put(session.getClientId(), session);
        pluginService.connect(session.getClientId(), session.getUsername());
    }


    /**
     * 清除Session信息、订阅信息、Session自身状态以及断开本地的Channel
     * 这里的操作暂时没有考虑到事务的问题,这里都queue也没有清理
     *
     * @param remove
     */
    private void cleanSession(Session remove) {
        // 这里先删除缓存
        String clientId = remove.getClientId();
        long tick = versionService.tick(AllConstants.QUEUE_VERSION_KEY, clientId);
        if (!eventService.publishCleanQueue(clientId, tick, null)) {
            log.error("Fail to cleanQueue for clientId:[{}]", clientId);
            return;
        }
        log.info("CleanSession for clientId:[{}]", clientId);
        subscriptionService.cleanSubscription(clientId);
        remove(clientId);
    }

    /**
     * 将该Session中的订阅信息,添加到订阅树中
     * 注意,该Session中qos1消息的补发还是依靠定时处理,不在这里处理
     *
     * @param session
     */
    private void reconnect(Session session) {
        // TODO: 2022/9/26 这里不能从内存中获取数据了,因为此刻内存中的数据可能已经在上次的disconnect中被清除掉了
        Map<String, Integer> subscription = subscriptionService.findSubscription(session.getClientId());
        if (subscription != null) {
            List<Subscription> subscriptions = subscription.entrySet().stream()
                    .map(e -> new Subscription(e.getKey(), e.getValue())).collect(Collectors.toList());
            subscriptions.forEach(s -> memorySubscriptionService.subscribe(s, session.getClientId()));
            session.setSessionService(this);
        }
    }

    @Override
    public Consumer<Event> connectEventSyncConsumer() {
        return event -> {
            ConnectionEvent connectionEvent = JSON.parseObject(event.getBody(), ConnectionEvent.class);
            doOpenSession(Session.buildSession(connectionEvent), event.getTick(), false);
        };
    }

    @Override
    public Consumer<Event> connectEventPersistConsumer() {
        return event -> {
            if (versionService.isExpired(AllConstants.SESSION_VERSION_KEY, event.getClientId(), event.getTick())) {
                log.warn("Drop expired connect event for [persist], clientId:[{}], tick:[{}]", event.getClientId(), event.getTick());
                return;
            }

            ConnectionEvent connectionEvent = JSON.parseObject(event.getBody(), ConnectionEvent.class);
            // 判断是否需要持久化,仅持久化不去操作内存
            if (!connectionEvent.isCleanSession()) {
                saveSession(Session.buildSession(connectionEvent));
            }
        };
    }

    @Override
    public CompletableFuture<Boolean> closeSession(String clientId, String username, DisconnectType type) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            long tick = versionService.tick(AllConstants.SESSION_VERSION_KEY, clientId);
            DisconnectionEvent event = DisconnectionEvent.buildEvent(type, username);
            if (!eventService.publishDisconnect(clientId, tick, JSON.toJSONBytes(event))) {
                return false;
            }
            return doCloseSession(clientId, tick, username, type);
        }, executorService);
    }

    public boolean doCloseSession(String clientId, long tick, String username, DisconnectType type) {
        if (versionService.isExpired(AllConstants.SESSION_VERSION_KEY, clientId, tick)) {
            log.warn("Drop expired disconnect event for [sync], clientId:[{}], tick:[{}]", clientId, tick);
            return false;
        }

        Session session = get(clientId);
        if (session != null) {
            session.close();
            if (session.isCleanSession()) {
                cleanSession(session);
            } else {
                // cleanSession=false,需要接收qos1的消息,这里仅取消qos0的订阅信息
                subscribeQoS0Topics(clientId).forEach(t -> subscriptionService.unsubscribe(t, clientId));
            }
        }
        pluginService.disconnect(clientId, username, type);
        return true;
    }

    @Override
    public Consumer<Event> disconnectEventSyncConsumer() {
        return event -> {
            DisconnectionEvent disconnectionEvent = JSON.parseObject(event.getBody(), DisconnectionEvent.class);
            doCloseSession(event.getClientId(), event.getTick(),
                    disconnectionEvent.getUsername(), disconnectionEvent.getType());
        };
    }

    @Override
    public Consumer<Event> disconnectEventPersistConsumer() {
        return event -> {
            if (versionService.isExpired(AllConstants.SESSION_VERSION_KEY, event.getClientId(), event.getTick())) {
                log.warn("Drop expired disconnect event for [persist], clientId:[{}], tick:[{}]", event.getClientId(), event.getTick());
            }

            // 无论是否需要持久化,这里都无需操作
        };
    }

    @Override
    public CompletableFuture<Boolean> subscribe(Subscription subscription, String clientId,
                                                boolean cleanSession, String username) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            SubscriptionEvent event = SubscriptionEvent.buildEvent(subscription.getTopicFilter(),
                    subscription.getQos(), cleanSession, username);
            String id = clientId + "-" + subscription.getTopicFilter();
            long tick = versionService.tick(AllConstants.SUBSCRIPTION_VERSION_KEY, id);
            if (!eventService.publishSubscribe(clientId, tick, JSON.toJSONBytes(event))) {
                return false;
            }
            return doSubscribe(subscription, clientId, tick, username);
        }, executorService);
    }

    private boolean doSubscribe(Subscription subscription, String clientId, long tick, String username) {
        String id = clientId + "-" + subscription.getTopicFilter();
        if (versionService.isExpired(AllConstants.SUBSCRIPTION_VERSION_KEY, id, tick)) {
            log.warn("Drop expired subscribe event for [sync], clientId:[{}], topic:[{}], tick:[{}]"
                    , clientId, subscription.getTopicFilter(), tick);
            return false;
        }
        subscriptionService.subscribe(subscription, clientId);
        pluginService.subscribe(clientId, username, subscription);
        Session session = get(clientId);
        if (session != null) {
            sendRetain(subscription, session);
        }
        return true;
    }

    @Override
    public Consumer<Event> subscribeEventSyncConsumer() {
        return event -> {
            SubscriptionEvent subscriptionEvent = JSON.parseObject(event.getBody(), SubscriptionEvent.class);
            doSubscribe(new Subscription(subscriptionEvent.getTopicFilter(), subscriptionEvent.getQos()),
                    event.getClientId(), event.getTick(), subscriptionEvent.getUsername());
        };
    }

    @Override
    public Consumer<Event> subscribeEventPersistConsumer() {
        return event -> {
            SubscriptionEvent subscriptionEvent = JSON.parseObject(event.getBody(), SubscriptionEvent.class);
            String id = event.getClientId() + "-" + subscriptionEvent.getTopicFilter();
            if (versionService.isExpired(AllConstants.SUBSCRIPTION_VERSION_KEY, id, event.getTick())) {
                log.warn("Drop expired subscribe event for [persist], clientId:[{}], topic:[{}], tick:[{}]"
                        , event.getClientId(), subscriptionEvent.getTopicFilter(), event.getTick());
                return;
            }

            if (!subscriptionEvent.isCleanSession()) {
                subscriptionService.saveSubscription(subscriptionEvent.getTopicFilter(),
                        subscriptionEvent.getQos(), event.getClientId());
            }
        };
    }

    private void sendRetain(Subscription subscription, Session session) {
        RetainMessage retainMessage = retainService.getRetain(subscription.getTopicFilter());
        if (retainMessage != null) {
            log.info("sendRetain for topic:[{}] clientId:[{}]", subscription.getTopicFilter(), session.getClientId());
            int qos = QoSUtils.matchQoS(retainMessage.getQos(), subscription.getQos());
            if (qos == 0) {
                sendMessage(Sets.newHashSet(session), retainMessage.getTopic(),
                        retainMessage.getPayload(),
                        session.nextMessageId(), qos);
            } else {
                handleQoS1(Sets.newHashSet(session), retainMessage.getTopic(),
                        retainMessage.getPayload(),
                        session.nextMessageId(), retainMessage.getSenderId());
            }
        }
    }

    @Override
    public CompletableFuture<Boolean> unsubscribe(String topic, String clientId, boolean cleanSession, String username) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            String id = clientId + "-" + topic;
            long tick = versionService.tick(AllConstants.SUBSCRIPTION_VERSION_KEY, id);
            UnSubscriptionEvent event = UnSubscriptionEvent.buildEvent(topic, cleanSession, username);
            if (!eventService.publishUnsubscribe(clientId, tick, JSON.toJSONBytes(event))) {
                return false;
            }
            return doUnsubscribe(topic, clientId, tick, username);
        }, executorService);
    }

    private boolean doUnsubscribe(String topic, String clientId, long tick, String username) {
        String id = clientId + "-" + topic;
        if (versionService.isExpired(AllConstants.SUBSCRIPTION_VERSION_KEY, id, tick)) {
            log.warn("Drop expired unsubscribe event for [sync], clientId:[{}], topic:[{}], tick:[{}]"
                    , clientId, topic, tick);
            return false;
        }

        subscriptionService.unsubscribe(topic, clientId);
        pluginService.unsubscribe(clientId, username, topic);
        return true;
    }

    @Override
    public Consumer<Event> unsubscribeEventSyncConsumer() {
        return event -> {
            UnSubscriptionEvent unsubscriptionEvent = JSON.parseObject(event.getBody(), UnSubscriptionEvent.class);
            doUnsubscribe(unsubscriptionEvent.getTopic(), event.getClientId(),
                    event.getTick(), unsubscriptionEvent.getUsername());
        };
    }

    @Override
    public Consumer<Event> unsubscribeEventPersistConsumer() {
        return event -> {
            UnSubscriptionEvent unsubscriptionEvent = JSON.parseObject(event.getBody(), UnSubscriptionEvent.class);
            String id = event.getClientId() + "-" + unsubscriptionEvent.getTopic();
            if (versionService.isExpired(AllConstants.SUBSCRIPTION_VERSION_KEY, id, event.getTick())) {
                log.warn("Drop expired subscribe event for [persist], clientId:[{}], topic:[{}], tick:[{}]"
                        , event.getClientId(), unsubscriptionEvent.getTopic(), event.getTick());
                return;
            }

            if (!unsubscriptionEvent.isCleanSession()) {
                subscriptionService.deleteSubscription(unsubscriptionEvent.getTopic(), event.getClientId());
            }
        };
    }

    @Override
    public CompletableFuture<Void> handlePublish(String senderId, String topic, byte[] payload,
                                                 int messageId, int qos, boolean dup, boolean retain) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            if (retain) {
                RetainMessage retainMessage = new RetainMessage(qos, topic, payload, senderId);
                long tick = versionService.tick(AllConstants.RETAIN_VERSION_KEY, topic);
                if (!eventService.publishAddRetain(senderId, tick, JSON.toJSONBytes(retainMessage))) {
                    return;
                }
            }
            Map<Session, Integer> subscribeSession = findSubscription(topic);
            if (qos == 0) {
                sendMessage(subscribeSession.keySet(), topic, payload, 0, 0);
            } else {
                // 忽略了dup标识,全部按第一次来处理
                Set<Session> qos0 = Sets.newHashSet();
                Set<Session> qos1 = Sets.newHashSet();
                // 判断订阅者的qos
                subscribeSession.forEach((k, v) -> {
                    if (v != 0) {
                        qos1.add(k);
                    } else {
                        qos0.add(k);
                    }
                });
                sendMessage(qos0, topic, payload, 0, 0);
                handleQoS1(qos1, topic, payload, messageId, senderId);
            }
        }, executorService);
    }

    /**
     * list subscription for this topic
     * contains subscription which disconnected session's qos1 topic
     * <p>
     * https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#clean-session-flag
     *
     * @param topic
     * @return
     */
    private Map<Session, Integer> findSubscription(String topic) {
        Map<Session, Integer> sessions = Maps.newHashMap();
        subscriptionService.subscribers(topic).forEach((k, v) -> {
            Session s = get(k);
            if (s != null) {
                sessions.put(s, v);
            } else {
                // TODO: 2022/8/3 这里找不到会话信息是否需要清理一下无效的订阅信息呢
            }
        });
        return sessions;
    }

    private void handleQoS1(Set<Session> targets, String topic, byte[] payload,
                            int messageId, String senderId) {
        targets.forEach(s -> {
            // 由于QoS1提供了inflight接受窗口的限制,所以这里的流程有所调整
            QueueMessage queueMessage = new QueueMessage(messageId, MqttQoS.AT_LEAST_ONCE.value(),
                    topic, payload, senderId, s.getClientId());
            if (!queueService.addQueue(queueMessage)) {
                return;
            }
            sendMessage(Sets.newHashSet(s), topic, payload, messageId, 1);
        });
    }


    private void sendMessage(Set<Session> targets, String topic, byte[] payload,
                             int messageId, int qos) {
        targets.stream()
                .filter(Session::connected)
                .forEach(s -> s.sendMessage(payload, messageId, topic, qos, false));
    }

    @Override
    public boolean removeInflight(int messageId, String clientId) {
        long tick = versionService.tick(AllConstants.QUEUE_VERSION_KEY, clientId);
        return eventService.publishRemoveQueue(clientId, tick, JSON.toJSONBytes(messageId));
    }

    @Override
    public Consumer<Event> removeQueueEventPersistConsumer() {
        return event -> {
            if (versionService.isExpired(AllConstants.QUEUE_VERSION_KEY,
                    event.getClientId(), event.getTick())) {
                log.warn("Drop expired removeQueue event for [persist], clientId:[{}], tick:[{}]", event.getClientId(), event.getTick());
                return;
            }

            int messageId = JSON.parseObject(event.getBody(), Integer.class);
            queueService.removeInflight(messageId, event.getClientId());
        };
    }

    @Override
    public Consumer<Serializable> messageConsumer() {
        return o -> {
            if (o instanceof PublishMessage) {
                PublishMessage message = (PublishMessage) o;
                AbstractSessionService.this.handlePublish(message.getClientId(), message.getTopic(),
                        message.getPayload(),
                        message.getMessageId(), message.getQos(), message.isDup(), false);
            } else if (o instanceof Metric) {
                Metric metric = (Metric) o;
                AbstractSessionService.this.handlePublish(SYS_CLIENT_ID, metric.topic(),
                        metric.jsonString().getBytes(StandardCharsets.UTF_8),
                        0, 0, false, false);
            }
        };
    }


    private List<String> subscribeQoS0Topics(String clientId) {
        Map<String, Integer> subscription = subscriptionService.findSubscription(clientId);
        if (subscription != null) {
            return subscriptionService.findSubscription(clientId).entrySet().stream()
                    .filter(e -> e.getValue() == 0).map(Map.Entry::getKey).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Consumer<Event> addRetainEventPersistConsumer() {
        return event -> {
            RetainMessage retainMessage = JSON.parseObject(event.getBody(), RetainMessage.class);
            if (versionService.isExpired(AllConstants.RETAIN_VERSION_KEY,
                    retainMessage.getTopic(), event.getTick())) {
                log.warn("Drop expired addRetain event for [persist], topic:[{}], tick:[{}]", retainMessage.getTopic(), event.getTick());
                return;
            }

            retainService.addRetain(retainMessage);
        };
    }

    @Override
    public Consumer<Event> addQueueEventPersistConsumer() {
        return event -> {
            if (versionService.isExpired(AllConstants.QUEUE_VERSION_KEY,
                    event.getClientId(), event.getTick())) {
                log.warn("Drop expired addQueue event for [persist], clientId:[{}], tick:[{}]", event.getClientId(), event.getTick());
                return;
            }
            QueueMessage queueMessage = JSON.parseObject(event.getBody(), QueueMessage.class);

            queueService.addQueue(queueMessage);
        };
    }

    @Override
    public Consumer<Event> cleanQueueEventPersistConsumer() {
        return event -> {
            if (versionService.isExpired(AllConstants.QUEUE_VERSION_KEY,
                    event.getClientId(), event.getTick())) {
                log.warn("Drop expired cleanQueue event for [persist], clientId:[{}], tick:[{}]", event.getClientId(), event.getTick());
                return;
            }

            queueService.cleanQueue(event.getClientId());
        };
    }

    protected Session get(String clientId) {
        return SESSIONS.get(clientId);
    }

    protected Session put(String clientId, Session session) {
        return SESSIONS.put(clientId, session);
    }

    protected Session remove(String clientId) {
        return SESSIONS.remove(clientId);
    }

    @Override
    public void setSubscriptionService(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void setRetainService(RetainService retainService) {
        this.retainService = retainService;
    }

    @Override
    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public void setMetricService(MetricService metricService) {
        this.metricService = metricService;
    }

    @Override
    public void setPluginService(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @Override
    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }

}
