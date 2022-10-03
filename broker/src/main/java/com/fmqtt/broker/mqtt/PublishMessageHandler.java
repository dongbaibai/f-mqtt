package com.fmqtt.broker.mqtt;

import com.fmqtt.authorization.Action;
import com.fmqtt.authorization.AuthorizationService;
import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.util.ByteBufUtils;
import com.fmqtt.common.util.MQTTMessageUtils;
import com.fmqtt.common.util.TopicUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.Session;
import com.fmqtt.session.SessionService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublishMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(PublishMessageHandler.class);
    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final MessageDispatcher messageDispatcher;

    public PublishMessageHandler(SessionService sessionService,
                                 AuthorizationService authorizationService,
                                 MessageDispatcher messageDispatcher) {
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final byte[] payload = ByteBufUtils.bytes((ByteBuf) msg.payload());
        ReferenceCountUtil.safeRelease(msg);
        final MqttPublishMessage mqttMessage = (MqttPublishMessage) msg;
        final String clientId = ChannelInfo.clientId(ctx.channel());
        final String topic = mqttMessage.variableHeader().topicName();
        final String username = ChannelInfo.username(ctx.channel());
        if (!isSystemTopic(topic)) {
            log.error("Can not publish message to system topic:[{}]", topic);
            sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }

        if (!TopicUtils.isValid(topic)) {
            log.error("Invalid Topic to publish:" + topic);
            sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }

        final int messageId = mqttMessage.variableHeader().packetId();
        final MqttQoS qos = mqttMessage.fixedHeader().qosLevel();
        final boolean dup = mqttMessage.fixedHeader().isDup();
        final boolean retain = mqttMessage.fixedHeader().isRetain();

        log.info("Handle PUBLISH message for dup:[{}], clientId:[{}], topic:[{}], messageId:[{}], qos:[{}], retain:[{}]",
                dup, clientId, topic, messageId, qos, retain);

        if (!authorizationService.authorize(topic, username, clientId, Action.WRITE)) {
            log.error("Check WRITE Permission failed username:[{}], clientId:[{}], topic:[{}]"
                    , username, clientId, topic);
            sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }

        sessionService.handlePublish(clientId, topic, payload, messageId, qos.value(), dup, retain)
                .thenAcceptAsync((Void) -> {
                    if (qos.value() > 0) {
                        sendPubAck(ChannelInfo.session(ctx.channel()), messageId);
                    }
                }, messageDispatcher.getPublishExecutorService());
    }

    private boolean isSystemTopic(String topic) {
        return !AllConstants.SYS_TOPIC.equals(topic);
    }

    // https://www.eclipse.org/lists/paho-dev/msg03429.html
    // https://github.com/huaweicloud/huaweicloud-iot-device-sdk-java/issues/12
    // https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#retry,Message delivery retry
    private void sendPubAck(Session session, int messageId) {
        session.sendMessage(MQTTMessageUtils.buildPublishAck(messageId),
                throwable ->
                        log.error("PUBACK send failed messageId:[{}]", messageId, throwable));
    }

}
