package com.fmqtt.subscription;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.util.TopicUtils;
import com.fmqtt.subscription.exception.TrieException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Trie {

    private final static Logger log = LoggerFactory.getLogger(Trie.class);

    private TrieNode root = new TrieNode();

    void addNode(String topicFilter, String clientId, Integer qos) {
        String[] topics = TopicUtils.parseTopic(topicFilter);
        TrieNode current = root;
        int level = 0;
        while (level < topics.length) {
            String topic = topics[level];
            TrieNode trieNode = current.children.get(topic);
            if (trieNode == null) {
                trieNode = new TrieNode(current);
                current.children.put(topic, trieNode);
                log.debug("Create TrieNode, topic:[{}]", topic);
            }
            current = trieNode;
            level++;
        }

        // avoid to invoke getNode twice
        if (current.subscriptions.size() > BrokerConfig.subscriptionMaxClientCount) {
            log.error("Maximum client count exceeded:[{}], topic:[{}]"
                    , BrokerConfig.subscriptionMaxClientCount, topicFilter);
            return;
        }
        current.subscriptions.put(clientId, qos);
    }

    void removeNode(String topicFilter, String clientId) {
        String[] topics = TopicUtils.parseTopic(topicFilter);
        TrieNode current = root;
        int level = 0;
        while (level < topics.length) {
            String topic = topics[level];
            TrieNode trieNode = current.children.get(topic);
            if (trieNode == null) {
                log.info("Fail to removeNode:[{}]", topicFilter);
                return;
            }
            current = trieNode;
            level++;
        }

        current.subscriptions.remove(clientId);
        while (current.children.isEmpty() && current.subscriptions.isEmpty() && current.parent != null) {
            current.parent.children.remove(topics[--level]);
            current = current.parent;
        }
    }

    long countSubRecords() {
        try {
            return countLevelRecords(root);
        } finally {
        }
    }

    long countLevelRecords(TrieNode current) {
        if (current == null) {
            return 0;
        }
        if (current.children.isEmpty()) {
            return current.subscriptions.size();
        }
        long childrenCount = 0;
        for (Map.Entry<String, TrieNode> entry : current.children.entrySet()) {
            childrenCount += countLevelRecords(entry.getValue());
        }
        return childrenCount + current.subscriptions.size();
    }

    /**
     * choose higher qos in qos list for same client
     *
     * @param topicFilter
     * @return
     */
    @SuppressWarnings("unchecked")
    Map<String, Integer> getNode(String topicFilter) {
        try {
            Map<String, Integer> result = Maps.newConcurrentMap();
            String[] topics = TopicUtils.parseTopic(topicFilter);
            Map<String, List<Integer>> subscriptions = findSubscriptions(root, topics, 0, topics.length, false);
            subscriptions.forEach((c, qosList) -> {
                Integer qos = null;
                if (qosList.size() > 1) {
                    qos = Collections.max(qosList);
                } else {
                    qos = qosList.get(0);
                }
                result.put(c, qos);
            });
            return result;
        } catch (Throwable e) {
            throw new TrieException(topicFilter, e);
        }
    }

    Map<String, List<Integer>> findSubscriptions(TrieNode current, String[] topics, int level,
                                                 int maxLevel, boolean isNumberSign) {
        Map</* clientId */String, List<Integer>> result = new HashMap<>(16);
        // match the mqtt-topic leaf or match the leaf node of trie
        if (level == maxLevel || isNumberSign) {
            // #/ or #asd will not send by mqtt client
            current.subscriptions.forEach((key, value) -> result.put(key, Lists.newArrayList(value)));
        }
        // match the '#'
        TrieNode numberMatch = current.children.get(AllConstants.NUMBER_SIGN);
        if (numberMatch != null) {
            Map<String, List<Integer>> subscriptions = findSubscriptions(numberMatch, topics, level + 1, maxLevel, true);
            subscriptions.forEach((key, value) -> {
                List<Integer> Integer = result.computeIfAbsent(key, k -> Lists.newArrayList());
                Integer.addAll(value);
            });
        }
        // match the mqtt-topic path
        if (level < maxLevel && !current.children.isEmpty()) {
            // match the precise
            TrieNode trieNode = current.children.get(topics[level]);
            if (trieNode != null) {
                Map<String, List<Integer>> subscriptions = findSubscriptions(trieNode, topics, level + 1, maxLevel, false);
                subscriptions.forEach((key, value) -> {
                    List<Integer> Integer = result.computeIfAbsent(key, k -> Lists.newArrayList());
                    Integer.addAll(value);
                });
            }
            // match the '+'
            TrieNode plusMatch = current.children.get(AllConstants.PLUS_SIGN);
            if (plusMatch != null) {
                Map<String, List<Integer>> subscriptions = findSubscriptions(plusMatch, topics, level + 1, maxLevel, false);
                subscriptions.forEach((key, value) -> {
                    List<Integer> Integer = result.computeIfAbsent(key, k -> Lists.newArrayList());
                    Integer.addAll(value);
                });
            }
        }
        return result;
    }

    /**
     * Returns the approximate trie state
     *
     * @return
     */
    public Map<String, Map<String, Integer>> traverseAll() {
        Map<String, Map<String, Integer>> result = Maps.newConcurrentMap();
        StringBuilder builder = new StringBuilder(128);
        traverse(root, result, builder);
        return result;
    }

    private void traverse(Trie.TrieNode currentNode,
                          Map<String, Map<String, Integer>> result, StringBuilder builder) {
        currentNode.children.forEach((topicFilter, node) -> {
            int prev = builder.length();
            String currentTopicFilter = builder.toString();
            if (StringUtils.isNotBlank(currentTopicFilter) &&
                    !AllConstants.TOPIC_DELIMITER.equals(currentTopicFilter)) {
                builder.append(AllConstants.TOPIC_DELIMITER);
            }
            builder.append(topicFilter);
            traverse(node, result, builder);
            builder.delete(prev, builder.length());
        });
        currentNode.subscriptions.forEach((clientId, qos) -> {
            Map<String, Integer> subs = result.computeIfAbsent(builder.toString(), k -> Maps.newConcurrentMap());
            subs.put(clientId, qos);
        });
    }

    public TrieNode getRoot() {
        return root;
    }

    public void setRoot(TrieNode root) {
        this.root = root;
    }

    public static class TrieNode {
        public TrieNode parent;
        public Map</* topicFilter */String, TrieNode> children = Maps.newConcurrentMap();
        public Map</* clientId */String, /* qos for this topicFilter */Integer> subscriptions = Maps.newConcurrentMap();

        public TrieNode() {
        }

        public TrieNode(TrieNode parent) {
            this.parent = parent;
        }

        public TrieNode getParent() {
            return parent;
        }

        public void setParent(TrieNode parent) {
            this.parent = parent;
        }

        public Map<String, TrieNode> getChildren() {
            return children;
        }

        public void setChildren(Map<String, TrieNode> children) {
            this.children = children;
        }

        public Map<String, Integer> getSubscriptions() {
            return subscriptions;
        }

        public void setSubscriptions(Map<String, Integer> subscriptions) {
            this.subscriptions = subscriptions;
        }
    }


}
