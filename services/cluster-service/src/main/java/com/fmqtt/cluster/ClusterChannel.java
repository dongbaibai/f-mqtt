package com.fmqtt.cluster;

import com.fmqtt.common.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class ClusterChannel implements Channel {

    private final static Logger log = LoggerFactory.getLogger(ClusterChannel.class);

    private final String peerServerName;
    private final ClusterService clusterService;

    public ClusterChannel(String peerServerName, ClusterService clusterService) {
        this.peerServerName = peerServerName;
        this.clusterService = clusterService;
    }

    @Override
    public void close() {
        // this methods will close the connect between other broker, just do not close right now
    }

    @Override
    public void sendMessage(Object msg, Consumer<Throwable> fail) {
        clusterService.send(peerServerName, msg);
    }
}
