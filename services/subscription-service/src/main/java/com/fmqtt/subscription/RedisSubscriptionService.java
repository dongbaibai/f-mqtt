package com.fmqtt.subscription;

import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;
import com.google.common.collect.Maps;
import org.redisson.api.RMap;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Hash结构,key=「topic」:subscription,field=clientId,value=qos
 */
public class RedisSubscriptionService extends AbstractSubscriptionService {

    private final RedissonService redissonService;

    public RedisSubscriptionService(RedissonService redissonService) {
        this.redissonService = redissonService;
    }

    @Override
    protected Map<String, Map<String, Integer>> findAll() {
        ConcurrentMap<String, Map<String, Integer>> subscriptions = Maps.newConcurrentMap();
        Iterable<String> iterable = redissonService.getRedissonClient()
                .getKeys().getKeysByPattern("*:subscription*");
        Iterator<String> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            String topic = key.replace(":subscription", "");
            Map<String, Integer> subscriptionMap = subscriptions.computeIfAbsent(topic, k -> Maps.newConcurrentMap());
            subscriptionMap.putAll(redissonService.getRedissonClient().<String, Integer>getMap(key));
        }
        return subscriptions;
    }

    @Override
    public boolean saveSubscription(String topic, int qos, String clientId) {
        redissonService.getRedissonClient()
                .<String, Integer>getMap(AllConstants.key(topic, AllConstants.SUBSCRIPTION))
                .put(clientId, qos);
        return true;
    }

    @Override
    public boolean deleteSubscription(String topic, String clientId) {
        redissonService.getRedissonClient()
                .<String, Integer>getMap(AllConstants.key(topic, AllConstants.SUBSCRIPTION))
                .remove(clientId);
        return true;
    }

}
