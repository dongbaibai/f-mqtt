package com.fmqtt.subscription;

import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.common.subscription.Subscription;

import java.util.Map;

public interface SubscriptionService extends Lifecycle {

    boolean saveSubscription(String topic, int qos, String clientId);

    boolean deleteSubscription(String topic, String clientId);

    void subscribe(Subscription subscription, String clientId);

    void unsubscribe(String topic, String clientId);

    void cleanSubscription(String clientId);

    Map<String, Integer> findSubscription(String clientId);

    Map<String, Integer> subscribers(String topicFilter);

}
