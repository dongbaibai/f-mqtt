package com.fmqtt.broker.mqtt;

import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.util.MQTTMessageUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.session.Session;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingReqMessageHandler implements MQTTMessageHandler {

    private final static Logger log = LoggerFactory.getLogger(PingReqMessageHandler.class);

    @Override
    public void handle(MqttMessage msg, ChannelHandlerContext ctx) {
        final String clientId = ChannelInfo.clientId(ctx.channel());
        final Session session = ChannelInfo.session(ctx.channel());
        final String addr = TransportUtils.parseChannelRemoteAddr(ctx.channel());
        log.info("Handle PINGREQ message for addr:[{}], clientId:[{}]"
                , addr, clientId);
        // 直接发送,不必再扔回ExecutorService
        session.sendMessage(MQTTMessageUtils.buildPingResp(),
                throwable ->
                        log.error("PINGREQ send failed, addr:[{}], clientId:[{}]", addr, clientId, throwable));
    }

}
