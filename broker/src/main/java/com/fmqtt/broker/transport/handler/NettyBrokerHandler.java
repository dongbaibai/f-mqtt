package com.fmqtt.broker.transport.handler;

import com.fmqtt.broker.mqtt.MessageDispatcher;
import com.fmqtt.common.util.TransportUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.MQTT消息的格式验证
 * 2.将MQTT消息从Netty中流转到MessageDispatcher协议分发器
 */
@ChannelHandler.Sharable
public class NettyBrokerHandler extends ChannelInboundHandlerAdapter {

    private final static Logger log = LoggerFactory.getLogger(NettyBrokerHandler.class);

    private final MessageDispatcher messageDispatcher;

    public NettyBrokerHandler(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }

    /**
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            MqttMessage mqttMessage = validateMessage(msg);
            messageDispatcher.handle(mqttMessage, ctx);
        } catch (InvalidMessageException exception) {
            log.error("Invalid message for addr:[{}]",
                    TransportUtils.parseChannelRemoteAddr(ctx.channel()), exception);
            TransportUtils.closeChannel(ctx.channel());
        }

    }

    private MqttMessage validateMessage(Object message) {
        MqttMessage mqttMessage = (MqttMessage) message;
        if (mqttMessage.decoderResult().isFailure()) {
            throw new InvalidMessageException("Invalid message", mqttMessage.decoderResult().cause());
        }
        return mqttMessage;
    }


}
