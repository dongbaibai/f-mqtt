package com.fmqtt.common.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class TransportUtils {

    private final static Logger log = LoggerFactory.getLogger(TransportUtils.class);

    public static SocketAddress string2SocketAddress(final String addr) {
        int split = addr.lastIndexOf(":");
        String host = addr.substring(0, split);
        String port = addr.substring(split + 1);
        InetSocketAddress isa = new InetSocketAddress(host, Integer.parseInt(port));
        return isa;
    }

    public static String parseChannelRemoteAddr(final Channel channel) {
        if (null == channel) {
            return "";
        }
        SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";

        if (addr.length() > 0) {
            int index = addr.lastIndexOf("/");
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }
        return "";
    }

    public static void dropConnection(MqttConnectReturnCode code, Channel channel) {
        channel.writeAndFlush(MQTTMessageUtils.buildConnAck(code)).addListener(future -> {
            log.info("DropConnection code:[{}] send result:[{}]", code, future.isSuccess());
            TransportUtils.closeChannel(channel);
        });
    }

    public static void closeChannel(Channel channel) {
        final String addrRemote = parseChannelRemoteAddr(channel);
        channel.close().addListener((ChannelFutureListener)
                future -> log.info("closeChannel: close the connection to remote address[{}] result: {}"
                        , addrRemote, future.isSuccess()));
    }

}
