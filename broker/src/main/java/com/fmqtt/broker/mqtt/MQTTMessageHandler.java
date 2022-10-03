package com.fmqtt.broker.mqtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;

public interface MQTTMessageHandler {

    void handle(MqttMessage mqttMessage, ChannelHandlerContext ctx);

}
