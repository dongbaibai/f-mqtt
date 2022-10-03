package com.fmqtt.common;

import com.fmqtt.common.util.MQTTMessageUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;

public class RequestTask implements Runnable {
    private final Runnable runnable;
    private final long createTimestamp = System.currentTimeMillis();
    // 这里属于无状态,没必要和Session挂钩,只要Channel open就返回流控信息即可
    private final Channel channel;
    private boolean stopRun = false;

    public RequestTask(final Runnable runnable, final Channel channel) {
        this.runnable = runnable;
        this.channel = channel;
    }

    public long getCreateTimestamp() {
        return createTimestamp;
    }

    public boolean isStopRun() {
        return stopRun;
    }

    public void setStopRun(final boolean stopRun) {
        this.stopRun = stopRun;
    }

    @Override
    public void run() {
        if (!this.stopRun)
            this.runnable.run();
    }

    public static RequestTask cast(Runnable runnable) {
        if (runnable instanceof RequestTask) {
            return (RequestTask) runnable;
        }
        return null;
    }

    public void returnResponse() {
        if (channel.isOpen()) {
            channel.writeAndFlush(
                    MQTTMessageUtils.buildConnAck(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

}