package com.fmqtt.common.channel;

import com.fmqtt.common.util.TransportUtils;

import java.util.function.Consumer;

public class NettyChannel implements Channel {

    private io.netty.channel.Channel channel;

    private NettyChannel(io.netty.channel.Channel channel) {
        this.channel = channel;
    }

    public static NettyChannel build(io.netty.channel.Channel channel) {
        return new NettyChannel(channel);
    }

    @Override
    public void close() {
        TransportUtils.closeChannel(channel);
    }

    @Override
    public void sendMessage(Object msg, Consumer<Throwable> fail) {
        if (channel.isOpen()) {
            channel.writeAndFlush(msg).addListener(future -> {
                if (!future.isSuccess()) {
                    fail.accept(future.cause());
                }
            });
        }
    }

}
