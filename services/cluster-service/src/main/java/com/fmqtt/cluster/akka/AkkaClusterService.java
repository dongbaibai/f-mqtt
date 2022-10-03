package com.fmqtt.cluster.akka;

import akka.actor.ActorSelection;
import com.fmqtt.cluster.ClusterChannel;
import com.fmqtt.cluster.ClusterService;
import com.fmqtt.common.channel.Channel;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.util.AssertUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AkkaClusterService extends AbstractLifecycle implements ClusterService {

    private final static Logger log = LoggerFactory.getLogger(AkkaClusterService.class);

    private Bootstrap bootstrap;
    private Consumer<Serializable> messageConsumer;

    @Override
    public void start() {
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.serverName)
                , BrokerConfig.CLUSTER_SERVER_NAME + " MUST be at least 1 character");
        AssertUtils.isTrue(BrokerConfig.akkaPort != null
                , BrokerConfig.CLUSTER_AKKA_PORT + " MUST be nonnull");
        AssertUtils.isTrue(messageConsumer != null
                , "messageConsumer MUST be nonnull");
        bootstrap = Bootstrap.newInstance(BrokerConfig.serverName, messageConsumer);
        bootstrap.start(BrokerConfig.akkaPort);
        log.info("AkkaClusterService start successfully");
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        bootstrap.shutdown();
    }

    @Override
    public Channel buildChannel(String serverName) {
        return new ClusterChannel(serverName, this);
    }

    public Map<String, List<String>> get() {
        ClusterActor localActor = bootstrap.getLocalActor(ClusterActor.class);
        return localActor.services;
    }

    public void send(String serverName, Object msg) {
        if (!isRunning()) {
            return;
        }
        ClusterActor clusterActor = bootstrap.getLocalActor(ClusterActor.class);
        List<String> strings = clusterActor.services.get(serverName);
        if (CollectionUtils.isNotEmpty(strings)) {
            strings.forEach(s -> {
                ActorSelection actorSelection = clusterActor.actorSystem.actorSelection(s + "/user/*");
                actorSelection.tell(msg, clusterActor.self());
            });
        }
    }

    @Override
    public void setMessageConsumer(Consumer<Serializable> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

}
