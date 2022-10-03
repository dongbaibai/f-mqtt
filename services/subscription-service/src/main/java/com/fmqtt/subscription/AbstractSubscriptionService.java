package com.fmqtt.subscription;

import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.subscription.Subscription;
import com.google.common.collect.Maps;

import java.util.Map;

public abstract class AbstractSubscriptionService extends AbstractLifecycle
        implements SubscriptionService {

    // 对ClientId加锁,保证同一client的线程安全
    private final static Map</* clientId */String, Map</* topicFilter */String, Integer>> SUBSCRIPTIONS = Maps.newConcurrentMap();
    protected MemorySubscriptionService memorySubscriptionService;

    public AbstractSubscriptionService() {
        this.memorySubscriptionService = MemorySubscriptionService.getInstance();
    }

    @Override
    public void start() throws Exception {
        Map<String, Map<String, Integer>> subscriptions = findAll();
        SUBSCRIPTIONS.putAll(subscriptions);
        subscriptions.forEach((k, v) ->
                v.forEach((topic, qos) ->
                        memorySubscriptionService.subscribe(new Subscription(topic, qos), k)));

        super.start();
    }

    protected abstract Map<String, Map<String, Integer>> findAll();

    @Override
    public void subscribe(Subscription subscription, String clientId) {
        put(clientId, subscription);
        memorySubscriptionService.subscribe(subscription, clientId);
    }

    @Override
    public void unsubscribe(String topic, String clientId) {
        Map<String, Integer> subscriptions = get(clientId);
        if (subscriptions != null) {
            subscriptions.remove(topic);
            if (subscriptions.isEmpty()) {
                remove(clientId);
            }
        }
        memorySubscriptionService.unsubscribe(topic, clientId);
    }

    @Override
    public void cleanSubscription(String clientId) {
        Map<String, Integer> subscriptions = SUBSCRIPTIONS.remove(clientId);
        if (subscriptions != null) {
            subscriptions.keySet().forEach(t -> memorySubscriptionService.unsubscribe(t, clientId));
        }
//        return deleteSubscription(clientId);
    }

    @Override
    public Map<String, Integer> findSubscription(String clientId) {
        return SUBSCRIPTIONS.get(clientId);
    }

    @Override
    public Map<String, Integer> subscribers(String topicFilter) {
        return memorySubscriptionService.findSubscription(topicFilter);
    }

    protected void remove(String clientId) {
        SUBSCRIPTIONS.remove(clientId);
    }

    protected Map<String, Integer> get(String clientId) {
        return SUBSCRIPTIONS.computeIfAbsent(clientId, k -> Maps.newConcurrentMap());
    }

    protected void put(String clientId, Subscription subscription) {
        Map<String, Integer> map = SUBSCRIPTIONS.computeIfAbsent(clientId, k -> Maps.newConcurrentMap());
        map.put(subscription.getTopicFilter(), subscription.getQos());
    }

    protected void putAll(String clientId, Map<String, Integer> subscription) {
        Map<String, Integer> map = SUBSCRIPTIONS.computeIfAbsent(clientId, k -> Maps.newConcurrentMap());
        map.putAll(subscription);
    }

}
