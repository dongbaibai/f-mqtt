package com.fmqtt.cluster;

import com.fmqtt.common.channel.Channel;
import com.fmqtt.common.lifecycle.Lifecycle;

import java.io.Serializable;
import java.util.function.Consumer;

public interface ClusterService extends Lifecycle {

    Channel buildChannel(String serverName);

    void send(String serverName, Object msg);

    void setMessageConsumer(Consumer<Serializable> messageConsumer);

}
