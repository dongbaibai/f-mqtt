package com.fmqtt.broker.mqtt;

import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.SessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 断开连接控制报文处理器
 * https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#disconnect
 */
public class DisconnectMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(DisconnectMessageHandler.class);
    private final SessionService sessionService;
    private final MessageDispatcher messageDispatcher;
    public DisconnectMessageHandler(SessionService sessionService,
                                    MessageDispatcher messageDispatcher) {
        this.sessionService = sessionService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final String username = ChannelInfo.username(ctx.channel());
        final String clientId = ChannelInfo.clientId(ctx.channel());
        final String addr = TransportUtils.parseChannelRemoteAddr(ctx.channel());
        log.info("Handle DISCONNECT message for addr:[{}], username:[{}], clientId:[{}]"
                , addr, username, clientId);

        sessionService.closeSession(clientId, username, DisconnectType.SUBJECTIVE)
                .thenAcceptAsync(result -> {
                    if (!result) {
                        log.error("Fail to disconnect for addr:[{}], username:[{}], clientId:[{}]"
                                , addr, username, clientId);
                        TransportUtils.dropConnection(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, ctx.channel());
                    }
                }, messageDispatcher.getDisconnectExecutorService());
    }

}
