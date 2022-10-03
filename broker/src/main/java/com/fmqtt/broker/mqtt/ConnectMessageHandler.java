package com.fmqtt.broker.mqtt;

import com.fmqtt.authentication.AuthenticationService;
import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.channel.NettyChannel;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.common.message.Will;
import com.fmqtt.common.util.MQTTMessageUtils;
import com.fmqtt.common.util.QoSUtils;
import com.fmqtt.common.util.TopicUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.SessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.*;

/**
 * 连接控制报文处理器
 * https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#connect
 */
public class ConnectMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(ConnectMessageHandler.class);
    private final AuthenticationService authenticationService;
    private final SessionService sessionService;
    private final MessageDispatcher messageDispatcher;
    public ConnectMessageHandler(SessionService sessionService,
                                 AuthenticationService authenticationService,
                                 MessageDispatcher messageDispatcher) {
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        // 从连接控制报文中获取信息
        MqttConnectMessage mqttConnectMessage = (MqttConnectMessage) msg;
        String clientId = mqttConnectMessage.payload().clientIdentifier();
        final String username = mqttConnectMessage.payload().userName();
        final boolean cleanSession = mqttConnectMessage.variableHeader().isCleanSession();
        final int version = mqttConnectMessage.variableHeader().version();
        final String addr = TransportUtils.parseChannelRemoteAddr(ctx.channel());
        log.info("Handle CONNECT message addr:[{}], clientId:[{}], version:[{}], username:[{}]"
                , addr, clientId, version, username);

        // 校验mqtt版本
        if (!checkProtocolVersion(version)) {
            log.error("Unacceptable protocol addr:[{}], clientId:[{}], version:[{}], username:[{}]"
                    , addr, clientId, version, username);
            TransportUtils.dropConnection(CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, ctx.channel());
            return;
        }

        // 校验clientId
        clientId = checkClientId(clientId, cleanSession);
        if (clientId == null) {
            log.error("ClientId rejected addr:[{}], username:[{}]", addr, username);
            TransportUtils.dropConnection(CONNECTION_REFUSED_IDENTIFIER_REJECTED, ctx.channel());
            return;
        }

        // 身份认证
        if (!authenticationService.authenticate(username, mqttConnectMessage.payload().passwordInBytes(), clientId)) {
            log.error("Invalid authentication addr:[{}], username:[{}], clientId:[{}]",
                    addr, username, clientId);
            TransportUtils.dropConnection(CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD, ctx.channel());
            return;
        }

        // 遗愿topic校验,这里并未校验该client对该topic的权限,这个操作目前是放在发送遗愿时进行校验了
        if (mqttConnectMessage.variableHeader().isWillFlag() &&
                !TopicUtils.isValid(mqttConnectMessage.payload().willTopic())) {
            log.error("Invalid Topic for will, topic:[{}], username:[{}], clientId:[{}]"
                    , mqttConnectMessage.payload().willTopic(), username, clientId);
            TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, ctx.channel());
            return;
        }

        // 创建遗愿
        Will will = createWill(mqttConnectMessage, clientId);
        NettyChannel channel = NettyChannel.build(ctx.channel());
        String finalClientId = clientId;
        sessionService.openSession(clientId, username, cleanSession, will)
                .thenAcceptAsync(session -> {
                    if (session == null) {
                        log.error("Connect fails for addr:[{}], username:[{}], clientId:[{}]"
                                , addr, username, finalClientId);
                        TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, ctx.channel());
                        return;
                    }
                    log.info("Connect successfully addr:[{}], username:[{}], clientId:[{}]"
                            , addr, username, finalClientId);
                    session.setChannel(channel);

                    // 绑定信息到ChannelInfo中
                    ChannelInfo.clientId(ctx.channel(), session.getClientId());
                    ChannelInfo.username(ctx.channel(), username);
                    ChannelInfo.session(ctx.channel(), session);
                    session.sendMessage(MQTTMessageUtils.buildConnAck(CONNECTION_ACCEPTED),
                            throwable -> {
                                log.error("CONNACK send failed addr:[{}], username:[{}] clientId:[{}]"
                                        , addr, username, session.getClientId(), throwable);
                                sessionService.closeSession(finalClientId, username, DisconnectType.EXCEPTION);
                                TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, ctx.channel());
                            });
                }, messageDispatcher.getConnectExecutorService());
    }

    // 不支持v5
    private boolean checkProtocolVersion(int version) {
        return version == AllConstants.MQTT_3_1 || version == AllConstants.MQTT_3_1_1;
    }

    private String checkClientId(String clientId,
                                 boolean cleanSession) {
        if (StringUtils.isBlank(clientId)) {
            if (!BrokerConfig.allowZeroClientId) {
                return null;
            }
            // allowZeroClientId = true && cleanSession = false,缺乏唯一标识
            if (!cleanSession) {
                return null;
            }
            clientId = UUID.randomUUID().toString().replace("-", "");
            log.info("Generated clientId:[{}] ", clientId);
        }
        return clientId.length() > BrokerConfig.clientIdMaxLength ? null : clientId;
    }


    private Will createWill(MqttConnectMessage mqttMessage, String clientId) {
        Will will = null;
        if (mqttMessage.variableHeader().isWillFlag()) {
            String topic = mqttMessage.payload().willTopic();
            if (canCreateWill(mqttMessage.payload().willTopic(), clientId)) {
                RetainMessage retainMessage =
                        new RetainMessage(QoSUtils.adjustQoS(mqttMessage.variableHeader().willQos()),
                                topic, mqttMessage.payload().willMessageInBytes(), clientId);
                will = new Will(retainMessage, mqttMessage.variableHeader().isWillRetain());
            }
        }
        return will;
    }

    private boolean canCreateWill(String topic, String clientId) {
        if (BrokerConfig.willAllowTopic != null && !BrokerConfig.willAllowTopic.contains(topic)) {
            log.error("Topic:[{}] is not allow to create will", topic);
        } else if (BrokerConfig.willAllowClientIds != null && !BrokerConfig.willAllowClientIds.contains(clientId)) {
            log.error("ClientId:[{}] is not allow to create will", clientId);
        }
        return true;
    }

}
