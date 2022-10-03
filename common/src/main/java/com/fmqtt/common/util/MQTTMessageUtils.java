package com.fmqtt.common.util;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;

import java.util.List;

import static io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader.from;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;

public abstract class MQTTMessageUtils {

    public static MqttMessage buildConnAck(MqttConnectReturnCode code) {
        return MqttMessageBuilders.connAck().returnCode(code).build();
    }

    public static MqttMessage buildPingResp() {
        MqttFixedHeader pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false,
                MqttQoS.AT_MOST_ONCE, false, 0);
        return new MqttMessage(pingHeader);
    }

    public static MqttPublishMessage buildPublishMessage(String topic, ByteBuf payload,
                                                         int messageId, int qos, boolean dup) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, dup, MqttQoS.valueOf(qos), false, 0);
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topic, messageId);
        return new MqttPublishMessage(fixedHeader, varHeader, payload);
    }

    public static MqttMessage buildPublishAck(int messageId) {
        return MqttMessageBuilders.pubAck().packetId(messageId).build();
    }

    public static MqttMessage buildSubAck(Integer qos, int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false,
                MqttQoS.AT_MOST_ONCE, false, 0);
        MqttSubAckPayload payload = new MqttSubAckPayload(Lists.newArrayList(qos));
        return new MqttSubAckMessage(fixedHeader, from(messageId), payload);
    }

    public static MqttMessage buildUnSubAck(int messageId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false,
                AT_MOST_ONCE, false, 0);
        return new MqttUnsubAckMessage(fixedHeader, from(messageId));
    }


}
