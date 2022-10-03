package com.fmqtt.subscription;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.subscription.exception.SubscriptionLockTimeoutException;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySubscriptionService {

    private final static Logger log = LoggerFactory.getLogger(MemorySubscriptionService.class);

    private volatile static MemorySubscriptionService INSTANCE = null;

    private final transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private Trie trie;
    private Map</* topicFilter */String, Map</* clientId */String, /* qos */Integer>> hash;

    public static MemorySubscriptionService getInstance() {
        if (INSTANCE == null) {
            synchronized (MemorySubscriptionService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MemorySubscriptionService();
                }
            }
        }
        return INSTANCE;
    }

    private MemorySubscriptionService() {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                hash = Maps.newConcurrentMap();
                break;
            case TRIE:
                trie = new Trie();
                break;
            default:
                throw new IllegalArgumentException("Unknown SubscriptionMode:" + BrokerConfig.subscriptionMode);
        }
        if (BrokerConfig.subscriptionDebugEnable) {
            log.debug("Start to debugging trie, debugTrieIntervalMills:[{}]", BrokerConfig.subscriptionDebugIntervalMills);
            Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setNameFormat("DebugTrie-thread-%s").build()).scheduleWithFixedDelay(() -> {
                try {
                    doDebug();
                } catch (Throwable e) { /* ignore */ }
            }, BrokerConfig.subscriptionDebugIntervalMills, BrokerConfig.subscriptionDebugIntervalMills, TimeUnit.MILLISECONDS);
        }
    }

    private void doDebug() {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                log.debug("Current subscriptions:[{}]", hash);
            case TRIE:
                log.debug("Current subscriptions:[{}]", trie.traverseAll());
        }
    }

    public void subscribe(Subscription subscription, String clientId) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        boolean locked = false;
        try {
            locked = writeLock.tryLock(BrokerConfig.subscriptionLockTimeoutMills, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new SubscriptionLockTimeoutException("clientId:" + clientId);
            }
            String topic = subscription.getTopicFilter();
            log.info("ClientId:[{}] subscribe topic:[{}]", clientId, topic);
            doSubscribe(topic, clientId, subscription.getQos());
        } catch (InterruptedException ignore) {
        } finally {
            if (locked) {
                writeLock.unlock();
            }
        }
    }

    private void doSubscribe(String topicFilter, String clientId, Integer qos) {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                makeTip();
                Map<String, Integer> subscriptions = hash.computeIfAbsent(topicFilter, key -> Maps.newConcurrentMap());
                if (subscriptions.size() > BrokerConfig.subscriptionMaxClientCount) {
                    log.error("Maximum client count exceeded:[{}], topic:[{}]"
                            , BrokerConfig.subscriptionMaxClientCount, topicFilter);
                }
                subscriptions.put(clientId, qos);
                break;
            case TRIE:
                trie.addNode(topicFilter, clientId, qos);
        }
    }

    public void unsubscribe(String topic, String clientId) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        boolean locked = false;
        try {
            locked = writeLock.tryLock(BrokerConfig.subscriptionLockTimeoutMills, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new SubscriptionLockTimeoutException("clientId:" + clientId);
            }
            log.info("ClientId:[{}] unsubscribe topic:[{}]", clientId, topic);
            doUnsubscribe(topic, clientId);
        } catch (InterruptedException ignore) {
        } finally {
            if (locked) {
                writeLock.unlock();
            }
        }
    }

    private void doUnsubscribe(String topicFilter, String clientId) {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                makeTip();
                Map<String, Integer> subscriptions = hash.get(topicFilter);
                if (subscriptions != null) {
                    subscriptions.remove(clientId);
                    if (subscriptions.isEmpty()) {
                        hash.remove(topicFilter);
                    }
                }
                break;
            case TRIE:
                trie.removeNode(topicFilter, clientId);
        }
    }

    public Map<String, Integer> findSubscription(String topicFilter) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        Map<String, Integer> subscriptions = null;
        boolean locked = false;
        try {
            locked = readLock.tryLock(BrokerConfig.subscriptionLockTimeoutMills, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new SubscriptionLockTimeoutException("topicFilter:" + topicFilter);
            }

            subscriptions = doFindSubscription(topicFilter);
            log.info("FindSubscription:[{}] for topic:[{}]", subscriptions, topicFilter);
        } catch (InterruptedException ignore) {
        } finally {
            if (locked) {
                readLock.unlock();
            }
        }
        return subscriptions;
    }

    private Map<String, Integer> doFindSubscription(String topicFilter) {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                makeTip();
                return hash.get(topicFilter);
            case TRIE:
                return trie.getNode(topicFilter);
            default:
                return Collections.emptyMap();
        }
    }

    public long countSubRecords() {
        switch (BrokerConfig.subscriptionMode) {
            case HASH:
                return hash.size();
            case TRIE:
                return trie.countSubRecords();
            default:
                return -1L;
        }
    }

    private void makeTip() {
        log.warn("HASH subscription mode is not support for Topic wildcards");
    }

}
