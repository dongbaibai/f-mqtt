package com.fmqtt.broker.mqtt;

import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.util.MQTTMessageUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.Session;
import com.fmqtt.session.SessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UnSubscribeMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(UnSubscribeMessageHandler.class);

    private final SessionService sessionService;
    private final MessageDispatcher messageDispatcher;

    public UnSubscribeMessageHandler(SessionService sessionService,
                                     MessageDispatcher messageDispatcher) {
        this.sessionService = sessionService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final MqttUnsubscribeMessage mqttMessage = (MqttUnsubscribeMessage) msg;
        final String username = ChannelInfo.username(ctx.channel());
        final String clientId = ChannelInfo.clientId(ctx.channel());
        final List<String> topics = mqttMessage.payload().topics();
        if (topics.size() != 1) {
            log.error("Only one topic is supported per unsubscription, clientId:[{}], topic:[{}]", clientId, topics);
            sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }
        log.info("Handle UNSUBSCRIBE message for clientId:[{}] topics:[{}]", clientId, topics);
        // 必要的
        Session session = ChannelInfo.session(ctx.channel());
        sessionService.unsubscribe(topics.get(0), clientId, session.isCleanSession(), username)
                .thenAcceptAsync(result -> {
                    if (!result) {
                        log.error("Fail to unsubscribe for clientId:[{}]", clientId);
                        sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
                        TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, ctx.channel());
                    } else {
                        session.sendMessage(
                                MQTTMessageUtils.buildUnSubAck(mqttMessage.variableHeader().messageId()),
                                throwable ->
                                        log.error("UNSUBACK send failed, clientId:[{}]", clientId));
                    }
                }, messageDispatcher.getUnsubscribeExecutorService());

    }


}
