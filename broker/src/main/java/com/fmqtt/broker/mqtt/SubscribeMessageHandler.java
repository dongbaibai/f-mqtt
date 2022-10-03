package com.fmqtt.broker.mqtt;

import com.fmqtt.authorization.Action;
import com.fmqtt.authorization.AuthorizationService;
import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.common.util.MQTTMessageUtils;
import com.fmqtt.common.util.TopicUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.Session;
import com.fmqtt.session.SessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 订阅控制报文处理器
 * https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#subscribe
 */
public class SubscribeMessageHandler implements MQTTMessageHandler {
    private final static Logger log = LoggerFactory.getLogger(SubscribeMessageHandler.class);
    private final AuthorizationService authorizationService;
    private final SessionService sessionService;
    private final MessageDispatcher messageDispatcher;

    public SubscribeMessageHandler(SessionService sessionService,
                                   AuthorizationService authorizationService,
                                   MessageDispatcher messageDispatcher) {
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final String clientId = ChannelInfo.clientId(ctx.channel());
        final MqttSubscribeMessage mqttMessage = (MqttSubscribeMessage) msg;
        final String username = ChannelInfo.username(ctx.channel());
        final List<MqttTopicSubscription> mqttTopicSubscriptions = mqttMessage.payload().topicSubscriptions();
        // 校验订阅数量
        if (mqttTopicSubscriptions.size() != 1) {
            log.error("Only one topic is supported per subscription, clientId:[{}], subscriptionsSize:[{}]"
                    , clientId, mqttTopicSubscriptions.size());
            sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }

        AtomicReference<Subscription> subscription = new AtomicReference<>();
        for (MqttTopicSubscription mqttTopicSubscription : mqttTopicSubscriptions) {
            String t = mqttTopicSubscription.topicName();
            // 校验Topic合法性
            if (!TopicUtils.isValid(mqttTopicSubscription.topicName())) {
                log.error("Invalid topic to subscribe, topic:[{}]", mqttTopicSubscription.topicName());
                sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
                TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
                return;
            }
            // 权限校验
            else if (!authorizationService.authorize(t, username, clientId, Action.READ)) {
                log.error("Check READ Permission failed username:[{}], clientId:[{}], topic:[{}]"
                        , username, clientId, t);
                sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
                TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
                return;
            } else {
                // qos2 处理
                int qos = mqttTopicSubscription.qualityOfService() == MqttQoS.EXACTLY_ONCE ?
                        MqttQoS.AT_LEAST_ONCE.value() : mqttTopicSubscription.qualityOfService().value();
                subscription.set(new Subscription(mqttTopicSubscription.topicName(), qos));
            }
        }

        log.info("Handle SUBSCRIBE message for clientId:[{}] subscriptions:[{}]", clientId, subscription);
        // 必要的
        Session session = ChannelInfo.session(ctx.channel());
        sessionService.subscribe(subscription.get(), clientId, session.isCleanSession(), username)
            .thenAcceptAsync(result -> {
                if (!result) {
                    log.error("Fail to subscribe for clientId:[{}]", clientId);
                    sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
                    TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, ctx.channel());
                } else {
                    session.sendMessage(
                            MQTTMessageUtils.buildSubAck(subscription.get().getQos(), mqttMessage.variableHeader().messageId())
                            , throwable ->
                                    log.error("SUBACK send failed, cleanup topics:[{}]", subscription.get().getTopicFilter())
                    );
                }
            }, messageDispatcher.getSubscribeExecutorService());
    }

}