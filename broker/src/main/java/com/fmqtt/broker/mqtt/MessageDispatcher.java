package com.fmqtt.broker.mqtt;

import com.fmqtt.broker.util.ChannelInfo;
import com.fmqtt.common.RequestTask;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.common.util.ExecutorServiceUtils;
import com.fmqtt.common.util.TransportUtils;
import com.fmqtt.limiting.LimitingService;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 1.针对不同的MqttMessageType的消息配置不同的处理线程池,解耦「分治而治,为后续优先级做铺垫」
 * 2.根据MqttMessageType将消息投递到对应的线程池中
 * 3.统一的限流处理
 * 4.对无效协议的过滤
 */
public class MessageDispatcher extends AbstractLifecycle {

    private final static Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<MqttMessageType, Pair<ExecutorService, MQTTMessageHandler>> HANDLERS = Maps.newHashMap();
    private final ExecutorService DEFAULT_EXECUTOR_SERVICE;
    private LimitingService limitingService;

    public MessageDispatcher() {
        DEFAULT_EXECUTOR_SERVICE = ExecutorServiceUtils.defaultExecutorService("message-dispatcher-thread");
    }

    public void handle(MqttMessage msg, ChannelHandlerContext ctx) throws Exception {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(messageType);
        if (pair == null) {
            log.error("No MQTTMessageHandler for messageType:[{}]", messageType);
            return;
        }

        String resource = limitingService.resource(messageType);
        if (resource == null) {
            log.warn("Fail to handle empty resource");
            return;
        }
        if (!limitingService.acquire(resource)) {
            log.error("Flow control for resource:[{}]", resource);
            return;
        }
        pair.getLeft().execute(
            new RequestTask(() -> {
                try {
                    pair.getRight().handle(msg, ctx);
                } catch (Exception e) {
                    log.error("Error occurs to MQTTMessageHandler addr:[{}], clientId:[{}]",
                            TransportUtils.parseChannelRemoteAddr(ctx.channel()), ChannelInfo.clientId(ctx.channel()), e);
                    TransportUtils.closeChannel(ctx.channel());
                }
            }, ctx.channel()));
    }

    public void start() throws Exception {
        AssertUtils.isTrue(limitingService != null, "limitingService MUST be set");
    }

    @Override
    public void shutdown() throws Exception {
        for (Pair<ExecutorService, MQTTMessageHandler> pair : HANDLERS.values()) {
            pair.getLeft().shutdown();
        }
        super.shutdown();
    }

    public void registerHandler(MqttMessageType mqttMessageType, MQTTMessageHandler mqttMessageHandler) {
        registerHandler(mqttMessageType, mqttMessageHandler, DEFAULT_EXECUTOR_SERVICE);
    }

    public void registerHandler(MqttMessageType mqttMessageType, MQTTMessageHandler mqttMessageHandler, ExecutorService executorService) {
        Pair<ExecutorService, MQTTMessageHandler> exists = HANDLERS.put(mqttMessageType, Pair.of(executorService, mqttMessageHandler));
        AssertUtils.isTrue(exists == null
                , "Duplicate MQTTMessageHandler:" + mqttMessageType.name());
    }

    public void setLimitingService(LimitingService limitingService) {
        this.limitingService = limitingService;
    }


    public ExecutorService getConnectExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.CONNECT);
        AssertUtils.isTrue(pair != null, "ConnectExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getDisconnectExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.DISCONNECT);
        AssertUtils.isTrue(pair != null, "DisconnectExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getSubscribeExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.SUBSCRIBE);
        AssertUtils.isTrue(pair != null, "SubscribeExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getUnsubscribeExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.UNSUBSCRIBE);
        AssertUtils.isTrue(pair != null, "UnsubscribeExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getPingReqExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.PINGREQ);
        AssertUtils.isTrue(pair != null, "PingReqExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getPublishExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.PUBLISH);
        AssertUtils.isTrue(pair != null, "PublishExecutorService MUST be set");
        return pair.getLeft();
    }

    public ExecutorService getPubAckExecutorService() {
        Pair<ExecutorService, MQTTMessageHandler> pair = HANDLERS.get(MqttMessageType.PUBACK);
        AssertUtils.isTrue(pair != null, "PubAckExecutorService MUST be set");
        return pair.getLeft();
    }

}
