package com.fmqtt.plugin;

import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.subscription.Subscription;

import java.util.List;

public interface Plugin {

    String name();

    void connect(String clientId, String username);

    void disconnect(String clientId, String username, DisconnectType type);

    void subscribe(String clientId, String username, Subscription subscription);

    void unsubscribe(String clientId, String username, String topic);

}
