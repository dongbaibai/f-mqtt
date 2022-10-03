package com.fmqtt.retain;

import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;

/**
 * Hash结构,retain为key,topic为field,RetainMessage为value
 */
public class RedisRetainService extends AbstractRetainService {

    private final RedissonService redissonService;

    public RedisRetainService(RedissonService redissonService) {
        this.redissonService = redissonService;
    }

    @Override
    public RetainMessage loadRetain(String topic) {
        return redissonService.getRedissonClient()
                .<String, RetainMessage>getMap(AllConstants.RETAIN)
                .get(topic);
    }

    @Override
    protected boolean saveRetain(RetainMessage retainMessage) {
        if (retainMessage.getPayload().length == 0) {
            redissonService.getRedissonClient()
                    .<String, RetainMessage>getMap(AllConstants.RETAIN)
                    .remove(retainMessage.getTopic());
        } else {
            redissonService.getRedissonClient()
                    .<String, RetainMessage>getMap(AllConstants.RETAIN)
                    .put(retainMessage.getTopic(), retainMessage);
        }
        return true;
    }

}
