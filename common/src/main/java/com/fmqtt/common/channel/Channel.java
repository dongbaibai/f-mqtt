package com.fmqtt.common.channel;

import java.util.function.Consumer;

/**
 * NettyChannel for local connection
 * ClusterChannel for Cluster connection
 */
public interface Channel {

    void close();

    void sendMessage(Object msg, Consumer<Throwable> fail);

}
