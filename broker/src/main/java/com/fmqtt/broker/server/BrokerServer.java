package com.fmqtt.broker.server;

import com.fmqtt.authentication.AuthenticationService;
import com.fmqtt.authentication.file.PasswordFileAuthenticationService;
import com.fmqtt.authorization.AuthorizationService;
import com.fmqtt.authorization.PermitAllAuthorizationService;
import com.fmqtt.authorization.file.AclFileAuthorizationService;
import com.fmqtt.broker.mqtt.*;
import com.fmqtt.broker.transport.TransportServer;
import com.fmqtt.broker.transport.handler.IdleEventHandler;
import com.fmqtt.broker.transport.handler.NettyBrokerHandler;
import com.fmqtt.cluster.ClusterService;
import com.fmqtt.cluster.akka.AkkaClusterService;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.common.util.ExecutorServiceUtils;
import com.fmqtt.event.EventService;
import com.fmqtt.event.kafka.KafkaEventService;
import com.fmqtt.limiting.LimitingService;
import com.fmqtt.limiting.SentinelLimitingService;
import com.fmqtt.metric.MetricService;
import com.fmqtt.metric.handler.MessageMetricHandler;
import com.fmqtt.plugin.DefaultPluginService;
import com.fmqtt.plugin.Plugin;
import com.fmqtt.plugin.PluginService;
import com.fmqtt.queue.QueueService;
import com.fmqtt.queue.RedisQueueService;
import com.fmqtt.retain.RedisRetainService;
import com.fmqtt.retain.RetainService;
import com.fmqtt.session.SessionService;
import com.fmqtt.session.cluster.RedisSessionService;
import com.fmqtt.subscription.RedisSubscriptionService;
import com.fmqtt.subscription.SubscriptionService;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;
import com.fmqtt.thirdpart.version.RedisVersionService;
import com.fmqtt.thirdpart.version.VersionService;
import com.google.common.collect.Lists;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.commons.lang3.StringUtils;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class BrokerServer {
    private final static Logger log = LoggerFactory.getLogger(BrokerServer.class);

    private final static String OS_NAME = System.getProperty("os.name");
    private static boolean isLinuxPlatform = false;

    static {
        if (OS_NAME != null && OS_NAME.toLowerCase().contains("linux")) {
            isLinuxPlatform = true;
        }
    }

    public static boolean epoll() {
        return BrokerConfig.useEpollNativeSelector
                && isLinuxPlatform
                && Epoll.isAvailable();
    }

    private final List<Lifecycle> lifeCycles = Lists.newArrayList();

    private TransportServer transportServer;
    private AuthenticationService authenticationService;
    private AuthorizationService authorizationService;
    private SubscriptionService subscriptionService;
    private QueueService queueService;
    private SessionService sessionService;
    private ClusterService clusterService;
    private RetainService retainService;
    private PluginService pluginService;
    private EventService eventService;
    private LimitingService limitingService;
    private MessageMetricHandler messageMetricHandler;
    private MessageDispatcher messageDispatcher;
    private MetricService metricService;
    private VersionService versionService;
    private RedissonService redissonService;

    // TODO: 2022/7/8 这个流程重新处理一下
    public void start() throws Exception {
        initComponents();
        configureComponents();
        startComponents();
    }

    private void initComponents() throws Exception {
        this.redissonService = new RedissonService(BrokerServer.epoll());
        this.versionService = initVersionService();
        this.clusterService = initClusterService();
        this.authenticationService = initAuthenticationService();
        this.authorizationService = initAuthorizationService();
        this.subscriptionService = initSubscriptionService();
        this.retainService = initRetainService();
        this.sessionService = initSessionService();
        this.queueService = initQueueService();
        this.metricService = MetricService.dropwizardMetricService(sessionService.messageConsumer());
        this.pluginService = initPluginService();
        this.limitingService = initLimitingService();
        this.eventService = initEventService();
        if (BrokerConfig.metricEnable) {
            this.messageMetricHandler = new MessageMetricHandler(metricService);
        }
        this.messageDispatcher = new MessageDispatcher();
        this.transportServer = initTransportServer();
        collectLifeCycles();
    }

    private VersionService initVersionService() {
        RedisVersionService redisVersionService = new RedisVersionService();
        redisVersionService.setRedissonService(redissonService);
        return redisVersionService;
    }

    private void collectLifeCycles() {
        // 注意这里的顺序
        lifeCycles.add(transportServer);
        lifeCycles.add(eventService);
        lifeCycles.add(redissonService);
        lifeCycles.add(versionService);
        lifeCycles.add(authenticationService);
        lifeCycles.add(authorizationService);
        lifeCycles.add(queueService);
        lifeCycles.add(retainService);
        lifeCycles.add(subscriptionService);
        lifeCycles.add(sessionService);
        lifeCycles.add(pluginService);
        lifeCycles.add(limitingService);
        lifeCycles.add(metricService);
        lifeCycles.add(messageDispatcher);
    }

    private AuthenticationService initAuthenticationService() throws Exception {
        AuthenticationService authenticationService = null;
        String authenticatorClassName = BrokerConfig.authenticationClassName;
        if (StringUtils.isNotBlank(authenticatorClassName)) {
            authenticationService = Reflect.onClass(authenticatorClassName).as(AuthenticationService.class);
        }
        if (authenticationService == null) {
            authenticationService = new PasswordFileAuthenticationService();
        }
        log.info("Init AuthenticationService:[{}]", authenticationService.getClass().getSimpleName());
        return authenticationService;
    }

    private AuthorizationService initAuthorizationService() throws Exception {
        AuthorizationService authorizationService = null;
        String authorizationClassName = BrokerConfig.authorizationClassName;
        if (StringUtils.isNotBlank(authorizationClassName)) {
            authorizationService = Reflect.onClass(authorizationClassName).as(AuthorizationService.class);
        }
        if (authorizationService == null) {
            if (StringUtils.isNotBlank(BrokerConfig.aclFilePath)) {
                authorizationService = new AclFileAuthorizationService();
            } else {
                authorizationService = new PermitAllAuthorizationService();
            }
        }
        log.info("Init AuthorizationService:[{}]", authorizationService.getClass().getSimpleName());
        return authorizationService;
    }

    private ClusterService initClusterService() throws Exception {
        ClusterService clusterService = null;
        String clusterClassName = BrokerConfig.clusterClassName;
        if (StringUtils.isNotBlank(clusterClassName)) {
            clusterService = Reflect.onClass(clusterClassName).as(ClusterService.class);
        }
        if (clusterService == null) {
            clusterService = new AkkaClusterService();
        }
        log.info("Init ClusterService:[{}]", clusterService.getClass().getSimpleName());
        return clusterService;
    }

    private EventService initEventService() throws Exception {
        EventService eventService = null;
        String eventClassName = BrokerConfig.eventClassName;
        if (StringUtils.isNotBlank(eventClassName)) {
            eventService = Reflect.onClass(eventClassName).as(EventService.class);
        }
        if (eventService == null) {
            eventService = new KafkaEventService();
        }
        log.info("Init EventService:[{}]", eventService.getClass().getSimpleName());
        return eventService;
    }

    private SubscriptionService initSubscriptionService() {
        SubscriptionService subscriptionService = null;
        String subscriptionClassName = BrokerConfig.subscriptionClassName;
        if (StringUtils.isNotBlank(subscriptionClassName)) {
            subscriptionService = Reflect.onClass(subscriptionClassName).as(SubscriptionService.class);
        }
        if (subscriptionService == null) {
            subscriptionService = new RedisSubscriptionService(redissonService);
        }
        log.info("Init SubscriptionService:[{}]", subscriptionService.getClass().getSimpleName());
        return subscriptionService;
    }

    private QueueService initQueueService() {
        QueueService queueService = null;
        String queueClassName = BrokerConfig.queueClassName;
        if (StringUtils.isNotBlank(queueClassName)) {
            queueService = Reflect.onClass(queueClassName).as(QueueService.class);
        }
        if (queueService == null) {
            queueService = new RedisQueueService(redissonService);
        }
        log.info("Init QueueService:[{}]", queueService.getClass().getSimpleName());
        return queueService;
    }

    private RetainService initRetainService() {
        RetainService retainService = null;
        String retainClassName = BrokerConfig.retainClassName;
        if (StringUtils.isNotBlank(retainClassName)) {
            retainService = Reflect.onClass(retainClassName).as(RetainService.class);
        }
        if (retainService == null) {
            retainService = new RedisRetainService(redissonService);
        }
        log.info("Init RetainService:[{}]", retainService.getClass().getSimpleName());
        return retainService;
    }

    private PluginService initPluginService() {
        PluginService pluginService = null;
        String pluginClassName = BrokerConfig.pluginClassName;
        if (StringUtils.isNotBlank(pluginClassName)) {
            pluginService = Reflect.onClass(pluginClassName).as(PluginService.class);
        }
        if (pluginService == null) {
            pluginService = new DefaultPluginService();
        }
        log.info("Init PluginService:[{}]", pluginService.getClass().getSimpleName());

        if (StringUtils.isNotBlank(BrokerConfig.pluginItemClassNames)) {
            for (String className : BrokerConfig.pluginItemClassNames.split(",")) {
                Plugin plugin = Reflect.onClass(className.trim()).as(Plugin.class);
                pluginService.addPlugin(plugin);
                log.info("Init Plugin:[{}]", plugin.getClass().getSimpleName());
            }
        }

        return pluginService;
    }

    private SessionService initSessionService() {
        SessionService sessionService = null;
        String sessionClassName = BrokerConfig.sessionClassName;
        if (StringUtils.isNotBlank(sessionClassName)) {
            sessionService = Reflect.onClass(sessionClassName).as(SessionService.class);
        }
        if (sessionService == null) {
            sessionService = new RedisSessionService(redissonService);
        }
        log.info("Init SessionService:[{}]", sessionService.getClass().getSimpleName());
        return sessionService;
    }

    private LimitingService initLimitingService() {
        LimitingService limitingService = null;
        String limitingClassName = BrokerConfig.limitingClassName;
        if (StringUtils.isNotBlank(limitingClassName)) {
            limitingService = Reflect.onClass(limitingClassName).as(LimitingService.class);
        }
        if (limitingService == null) {
            limitingService = new SentinelLimitingService();
        }
        log.info("Init LimitingService:[{}]", limitingService.getClass().getSimpleName());
        return limitingService;
    }

    private TransportServer initTransportServer() {
        Consumer<ChannelPipeline> pipelineConsumer = pipeline -> {
            if (BrokerConfig.metricEnable) {
                pipeline.addLast("messageMetricHandler", messageMetricHandler);
            }
            pipeline
                .addLast("idleStateHandler", new IdleStateHandler(0, 0, BrokerConfig.keepalive))
                .addLast("idleEventHandler", new IdleEventHandler(sessionService, authorizationService))
                .addLast("decoder", new MqttDecoder(BrokerConfig.maxBytesInMessage))
                .addLast("encoder", MqttEncoder.INSTANCE)
                .addLast("handler", new NettyBrokerHandler(messageDispatcher));
        };
        return new TransportServer(pipelineConsumer);
    }

    private void configureComponents() {
        configureEventService();
        configureClusterService();
        configureMessageDispatcher();
        configureLimitingService();
        configureRetainService();
        configureSessionService();
    }
    
    private void configureEventService() {
        eventService.setConnectEventSyncConsumer(sessionService.connectEventSyncConsumer());
        eventService.setDisconnectEventSyncConsumer(sessionService.disconnectEventSyncConsumer());
        eventService.setSubscribeEventSyncConsumer(sessionService.subscribeEventSyncConsumer());
        eventService.setUnsubscribeEventSyncConsumer(sessionService.unsubscribeEventSyncConsumer());

        eventService.setConnectEventPersistConsumer(sessionService.connectEventPersistConsumer());
        eventService.setDisconnectEventPersistConsumer(sessionService.disconnectEventPersistConsumer());
        eventService.setSubscribeEventPersistConsumer(sessionService.subscribeEventPersistConsumer());
        eventService.setUnsubscribeEventPersistConsumer(sessionService.unsubscribeEventPersistConsumer());
        eventService.setAddRetainEventPersistConsumer(sessionService.addRetainEventPersistConsumer());
        eventService.setAddQueueEventPersistConsumer(sessionService.addQueueEventPersistConsumer());
        eventService.setRemoveQueueEventPersistConsumer(sessionService.removeQueueEventPersistConsumer());
        eventService.setCleanQueueEventPersistConsumer(sessionService.cleanQueueEventPersistConsumer());
    }

    private void configureRetainService() {
        retainService.setMetricService(metricService);
    }

    private void configureClusterService() {
        clusterService.setMessageConsumer(sessionService.messageConsumer());
    }
    
    private void configureMessageDispatcher() {
        messageDispatcher.registerHandler(MqttMessageType.CONNECT,
                new ConnectMessageHandler(sessionService, authenticationService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("connect-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.DISCONNECT,
                new DisconnectMessageHandler(sessionService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("disconnect-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.PINGREQ,
                new PingReqMessageHandler(),
                ExecutorServiceUtils.defaultExecutorService("ping-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.SUBSCRIBE,
                new SubscribeMessageHandler(sessionService, authorizationService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("subscribe-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.UNSUBSCRIBE,
                new UnSubscribeMessageHandler(sessionService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("unsubscribe-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.PUBLISH,
                new PublishMessageHandler(sessionService, authorizationService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("publish-message-handler-thread"));
        messageDispatcher.registerHandler(MqttMessageType.PUBACK,
                new PubAckMessageHandler(sessionService, messageDispatcher),
                ExecutorServiceUtils.defaultExecutorService("puback-message-handler-thread"));
        messageDispatcher.setLimitingService(limitingService);
    }
    
    private void configureLimitingService() {
        limitingService.registerResource(AllConstants.CONNECTION_RESOURCE_KEY,
                MqttMessageType.CONNECT, BrokerConfig.limitConnectionQps);
        limitingService.registerResource(AllConstants.DISCONNECTION_RESOURCE_KEY,
                MqttMessageType.DISCONNECT, BrokerConfig.limitDisconnectionQps);
        limitingService.registerResource(AllConstants.SUB_RESOURCE_KEY,
                MqttMessageType.SUBSCRIBE, BrokerConfig.limitSubQps);
        limitingService.registerResource(AllConstants.UNSUB_RESOURCE_KEY,
                MqttMessageType.UNSUBSCRIBE, BrokerConfig.limitUnSubQps);
        limitingService.registerResource(AllConstants.PING_RESOURCE_KEY,
                MqttMessageType.PINGREQ, BrokerConfig.limitPingQps);
        limitingService.registerResource(AllConstants.PUB_RESOURCE_KEY,
                MqttMessageType.PUBLISH, BrokerConfig.limitPubQps);
        limitingService.registerResource(AllConstants.PUBACK_RESOURCE_KEY,
                MqttMessageType.PUBACK, BrokerConfig.limitPubQps);
    }

    private void configureSessionService() {
        sessionService.setMetricService(metricService);
        sessionService.setSubscriptionService(subscriptionService);
        sessionService.setQueueService(queueService);
        sessionService.setClusterService(clusterService);
        sessionService.setEventService(eventService);
        sessionService.setRetainService(retainService);
        sessionService.setVersionService(versionService);
        sessionService.setPluginService(pluginService);
    }

    private void startComponents() throws Exception {
        for (Lifecycle lifeCycle : lifeCycles) {
            lifeCycle.start();
        }
    }

    public void shutdown() throws Exception {
        log.info("Stopping Broker");
        for (Lifecycle lifeCycle : lifeCycles) {
            lifeCycle.shutdown();
        }
        log.info("Broker stop successfully");
    }

}
