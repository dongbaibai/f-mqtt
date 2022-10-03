package com.fmqtt.cluster.akka;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseActor extends UntypedActor {

    protected final Map<String, List<String>> services = new HashMap<>();
    protected Cluster cluster = Cluster.get(getContext().system());
    protected String service;
    protected LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    protected ActorSystem actorSystem;
    protected Consumer<Serializable> messageConsumer;

    /**
     * 所需的服务
     *
     * @param bs
     */
    public BaseActor(Bootstrap bs) {
        this.actorSystem = bs.getActorSystem();
        this.service = bs.getService();
        this.messageConsumer = bs.getMessageConsumer();
        bs.actorCreated(this);
    }

    @Override
    public void preStart() throws Exception {
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
                ClusterEvent.MemberUp.class,
                ClusterEvent.MemberEvent.class,
                ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void postStop() throws Exception {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ClusterEvent.MemberUp) {
            ClusterEvent.MemberUp event = (ClusterEvent.MemberUp) message;
            this.up(event.member());
        } else if (message instanceof ClusterEvent.CurrentClusterState) {
            ClusterEvent.CurrentClusterState state = (ClusterEvent.CurrentClusterState) message;
            Iterable<Member> members = state.getMembers();
            for (Member o : members) {
                if (o.status().equals(MemberStatus.up())) {
                    this.up(o);
                }
            }
        } else if (message instanceof ClusterEvent.UnreachableMember) {
            ClusterEvent.UnreachableMember unreachable = (ClusterEvent.UnreachableMember) message;
            log.info("UnreachableMember:{}", unreachable.member());
            removeService(unreachable.member().address().toString());
        } else if (message instanceof ClusterEvent.MemberRemoved) {
            ClusterEvent.MemberRemoved removed = (ClusterEvent.MemberRemoved) message;
            log.info("MemberRemoved:{}", removed.member());
            removeService(removed.member().address().toString());
        } else if (message instanceof ClusterEvent.MemberEvent) {
            log.info("MemberEvent:{}", message);
        } else if (message instanceof Registration) {
            Registration r = (Registration) message;
            getContext().watch(getSender());
            this.addService(r.getService(), getSender().path().address().toString());
        } else if (message instanceof Terminated) {
            Terminated terminated = (Terminated) message;
            log.info("Terminated:{}", terminated.actor().path().address().toString());
            this.removeService(terminated.actor().path().address().toString());
        } else if (message instanceof Serializable) {
            this.messageConsumer.accept((Serializable) message);
        }
    }

    /**
     * 向新加入节点注册自己
     *
     * @param member
     */
    private void up(Member member) {
        ActorSelection as = getContext().actorSelection(member.address() + "/user/*");
        if (as != null) {
            as.tell(new Registration(service), self());
        }
    }

    /**
     * 添加一个service
     *
     * @param
     */
    private void addService(String name, String address) {
        if (service.equals(name)) {
            return;
        }
        List<String> list = this.services.computeIfAbsent(name, k -> Lists.newArrayList());
        if (!list.contains(address)) {
            log.info("addService name:[{}], address:[{}]", name, address);
            list.add(address);
        }
    }

    /**
     * 删掉一个slave
     *
     * @param address
     */
    private void removeService(String address) {
        for (List<String> item : this.services.values()) {
            if (item.remove(address)) {
                log.info("removeService address:[{}]", address);
                break;
            }
        }
    }

}
