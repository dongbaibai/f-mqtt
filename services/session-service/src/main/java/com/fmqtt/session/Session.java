package com.fmqtt.session;

import com.fmqtt.cluster.akka.PublishMessage;
import com.fmqtt.common.channel.Channel;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.events.ConnectionEvent;
import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.message.Will;
import com.fmqtt.common.util.ByteBufUtils;
import com.fmqtt.common.util.MQTTMessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Session implements Serializable {
    private static final long serialVersionUID = -6579317981266093895L;
    private final static Logger log = LoggerFactory.getLogger(Session.class);
    private final transient AtomicInteger lastMessageId = new AtomicInteger(0);
    private transient volatile Channel channel;
    private final Object channelLock = new Object();
    private String clientId;
    private String username;
    private boolean cleanSession;
    private Will will;
//    private Map<String, Integer> subscriptions = Maps.newConcurrentMap();
    private String serverName;
    private transient SessionService sessionService;

    public Session() {

    }

    public Session(String clientId, String username,
                   boolean cleanSession, Will will, String serverName) {
        this.clientId = clientId;
        this.username = username;
        this.cleanSession = cleanSession;
        this.will = will;
        this.serverName = serverName;
    }

    public boolean connected() {
        return channel != null;
    }

    public void close() {
        log.debug("Close session:[{}]", clientId);
        synchronized (channelLock) {
            if (localSession()) {
                if (channel != null) {
                    channel.close();
                }
            }
            channel = null;
        }
    }

    public int nextMessageId() {
        return lastMessageId.updateAndGet(v -> v == 65535 ? 1 : v + 1);
    }

//    public void subscribe(List<Subscription> subscriptions) {
//        subscriptions.forEach(s -> this.subscriptions.put(s.getTopicFilter(), s.getQos()));
//    }
//
//    public void unsubscribe(List<String> topics) {
//        topics.forEach(t -> this.subscriptions.remove(t));
//    }

    public void sendMessage(byte[] payload, int messageId, String topic,
                            int qos, boolean dup) {
        Object msg = null;
        if (localSession()) {
            msg = MQTTMessageUtils.buildPublishMessage(topic, ByteBufUtils.wrappedPayload(payload), messageId, qos, dup);
        } else {
            msg = new PublishMessage(topic, qos, clientId,
                    messageId, dup, payload, BrokerConfig.serverName);
        }
        sendMessage(msg, throwable ->
            log.error("SendMessage to clientId:[{}], topic:[{}] failed", clientId, topic, throwable));
    }

    
    public void sendMessage(Object msg, Consumer<Throwable> fail) {
        synchronized (channelLock) {
            if (channel != null) {
                channel.sendMessage(msg, throwable -> {
                    Session.this.sessionService.closeSession(clientId, username, DisconnectType.EXCEPTION);
                    fail.accept(throwable);
                });
            } else {
                log.error("The connection has been disconnected clientId:[{}]", clientId);
            }
        }
    }


    /**
     * 是否为本地的Session
     *
     * @return
     */
    public boolean localSession() {
        return BrokerConfig.serverName.equals(serverName);
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }


//    public Map<String, Integer> getSubscriptions() {
//        return subscriptions;
//    }

//    public void setSubscriptions(Map<String, Integer> subscriptions) {
//        this.subscriptions = subscriptions;
//    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Will getWill() {
        return will;
    }

    public void setWill(Will will) {
        this.will = will;
    }

//    public List<String> subscribeTopics() {
//        return Lists.newArrayList(subscriptions.keySet());
//    }

//    public List<String> subscribeQoS0Topics() {
//        return subscriptions.entrySet().stream()
//                .filter(entry -> entry.getValue() == 0)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public static Session buildSession(ConnectionEvent connectionEvent) {
        return new Session(connectionEvent.getClientId(), connectionEvent.getUsername(),
                connectionEvent.isCleanSession(), connectionEvent.getWill(), connectionEvent.getServerName());
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String toString() {
        return "Session{" +
                "lastMessageId=" + lastMessageId +
                ", channel=" + channel +
//                ", status=" + status +
                ", clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", cleanSession=" + cleanSession +
                ", will=" + will +
//                ", subscriptions=" + subscriptions +
                ", serverName='" + serverName + '\'' +
                ", sessionService=" + sessionService +
                '}';
    }
}
