package com.fmqtt.queue;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.message.QueueMessage;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * inflight,Hash结构,clientId+":"+inflight为key,messageId为field,value为QueueMessage
 * queue,Hash结构,clientId+":"+queueMap为key,messageId为field,value为QueueMessage,为了能够在Lua内很好的操作
 * queue,List结构,clientId+":"+queueList为key,messageId为value,为了保证顺序
 */
public class RedisQueueService extends AbstractQueueService {

    private final RedissonService redissonService;

    public RedisQueueService(RedissonService redissonService) {
        this.redissonService = redissonService;
    }

    @Override
    public int queueSize(String clientId) {
        return redissonService.getRedissonClient()
                .getList(AllConstants.key(clientId, AllConstants.QUEUE_LIST))
                .size();
    }

    @Override
    public int inflightSize(String clientId) {
        return redissonService.getRedissonClient()
                .getMap(AllConstants.key(clientId, AllConstants.INFLIGHT))
                .size();
    }

    @Override
    List<QueueMessage> adjust(String clientId) {
        String script =
                "local is = redis.call('hlen', KEYS[1]);" +
                "local qs = redis.call('llen', KEYS[2]);" +
                "local r = " + BrokerConfig.maxInflightMessages + " - is;" +
                "if (r > 0 and qs > 0) then " +
                    "for i = 0, r, 1 do " +
                        "qs = qs - 1;" +
                        "local mid = redis.call('lpop', KEYS[2]);" +
                        "local qm = redis.call('hget', KEYS[3], mid);" +
                        "if (qm ~= nil) then " +
                            "redis.call('hset', KEYS[1], mid, qm);" +
                            "redis.call('hdel', KEYS[3], mid);" +
                        "end;" +
                    "end;" +
                "end;" +
                "return redis.call('hvals', KEYS[1]);";
        return redissonService.executeList(script,
                Lists.newArrayList(AllConstants.key(clientId, AllConstants.INFLIGHT),
                        AllConstants.key(clientId, AllConstants.QUEUE_LIST),
                        AllConstants.key(clientId, AllConstants.QUEUE_MAP)));
    }

    @Override
    protected boolean saveQueue(QueueMessage queueMessage) {
        String clientId = queueMessage.getTargetId();
        String script =
                "if (redis.call('hlen', KEYS[1]) < " + BrokerConfig.maxInflightMessages + ") then " +
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]);" +
                    "return 1;" +
                "else " +
                    "redis.call('rpush', KEYS[2], ARGV[1]);" +
                    "redis.call('hset', KEYS[3], ARGV[1], ARGV[2]);" +
                    "return 0;" +
                "end";
        return redissonService.executeBoolean(script,
                Lists.newArrayList(AllConstants.key(clientId, AllConstants.INFLIGHT),
                        AllConstants.key(clientId, AllConstants.QUEUE_LIST),
                        AllConstants.key(clientId, AllConstants.QUEUE_MAP)),
                queueMessage.getMessageId(), queueMessage);
    }

    @Override
    protected boolean deleteQueue(int messageId, String clientId) {
        redissonService.getRedissonClient()
                .getMap(AllConstants.key(clientId, AllConstants.INFLIGHT))
                .remove(messageId);
        return true;
    }

    @Override
    protected boolean deleteAllQueue(String clientId) {
        String script =
                "redis.call('del', KEYS[1], KEYS[2], KEYS[3]);";
        redissonService.executeBoolean(script,
                Lists.newArrayList(AllConstants.key(clientId, AllConstants.INFLIGHT),
                        AllConstants.key(clientId, AllConstants.QUEUE_LIST),
                        AllConstants.key(clientId, AllConstants.QUEUE_MAP)));
        return true;
    }

}
