package com.fmqtt.broker.mqtt;

import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.queue.QueueService;
import com.fmqtt.session.Session;
import com.fmqtt.session.SessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class PubAckMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(PubAckMessageHandler.class);
    private final SessionService sessionService;
    private final MessageDispatcher messageDispatcher;

    public PubAckMessageHandler(SessionService sessionService,
                                MessageDispatcher messageDispatcher) {
        this.sessionService = sessionService;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        final String clientId = ChannelInfo.clientId(ctx.channel());
        log.info("Handle PUBACK message for messageId:[{}], clientId:[{}]"
                , messageId, clientId);

        CompletableFuture.supplyAsync(() -> {
            if (!sessionService.removeInflight(messageId, clientId)) {
                log.warn("Unknown PUBACK to removeInflight for clientId:[{}], messageId:[{}]"
                        , clientId, messageId);
            }
            return null;
        }, messageDispatcher.getPubAckExecutorService());

    }

}
