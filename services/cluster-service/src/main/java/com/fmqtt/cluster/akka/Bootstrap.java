package com.fmqtt.cluster.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.cluster.Cluster;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.util.AssertUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class Bootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(Bootstrap.class);
    private final Map<Class<? extends BaseActor>, ActorRef> localActorRefs;
    private final Map<Class<? extends BaseActor>, BaseActor> localActors;
    private Config config;
    private ActorSystem actorSystem;
    private Cluster cluster;
    private String service;
    private Inbox inbox;
    private Consumer<Serializable> messageConsumer;

    /**
     * 構造
     */
    private Bootstrap(String service, Consumer<Serializable> messageConsumer) {
        localActorRefs = new HashMap<>();
        localActors = new HashMap<>();
        this.service = service;
        this.messageConsumer = messageConsumer;
    }

    /**
     * 实例
     *
     * @param
     * @return
     */
    public static Bootstrap newInstance(String service, Consumer<Serializable> messageConsumer) {
        AssertUtils.isTrue(messageConsumer != null, "messageConsumer MUST be set");
        return new Bootstrap(service, messageConsumer);
    }

    /**
     * 启动
     *
     * @param port
     */
    public void start(int port) {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("akka.remote.netty.tcp.port", port);
        overrides.put("config.resource", AllConstants.DIRECTORY + "conf/application.conf");
        config = ConfigFactory.parseMap(overrides)
                .withFallback(ConfigFactory.load());

        this.actorSystem = ActorSystem.create("SYSTEM_ACTOR", config);
        this.inbox = Inbox.create(actorSystem);
        cluster = Cluster.get(actorSystem);
        Props prop = Props.create(ClusterActor.class, this).withDispatcher("dispatcher");
        ActorRef ar = actorSystem.actorOf(prop, "CLUSTER_ACTOR");
        localActorRefs.put(ClusterActor.class, ar);
    }

    public void shutdown() {
        cluster.shutdown();
        actorSystem.shutdown();
    }

    /**
     * actorSystem
     *
     * @return
     */
    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    /**
     * cluster
     *
     * @return
     */
    public Cluster getCluster() {
        return cluster;
    }

    /**
     * 本地的actorRef
     *
     * @param c
     * @return
     */
    public ActorRef getLocalActorRef(Class<? extends BaseActor> c) {
        return this.localActorRefs.get(c);
    }

    /**
     * 本地actor
     *
     * @param <T>
     * @param c
     * @return
     */
    public <T> T getLocalActor(Class<T> c) {
        return (T) this.localActors.get(c);
    }

    /**
     * 本地actorRef发送消息
     *
     * @param c
     * @param obj
     * @return
     */
    public Serializable locatActorSend(Class<? extends BaseActor> c, Serializable obj) {
        ActorRef ar = this.getLocalActorRef(c);
        if (ar != null) {
            inbox.send(ar, obj);
            try {
                return (Serializable) inbox.receive(Duration.create(5, TimeUnit.MINUTES));
            } catch (TimeoutException ex) {
                LOG.error("directSend error!" + obj);
            }
        }
        return null;
    }

    /**
     * 本地actor创建完毕
     *
     * @param
     */
    void actorCreated(BaseActor ba) {
        this.localActors.put(ba.getClass(), ba);
    }

    public String getService() {
        return service;
    }

    public Consumer<Serializable> getMessageConsumer() {
        return messageConsumer;
    }
}
