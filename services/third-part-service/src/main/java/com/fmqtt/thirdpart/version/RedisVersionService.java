package com.fmqtt.thirdpart.version;

import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;

import java.util.Collections;

/**
 * 版本服务通过Redis实现
 * Hash结构
 * session_version
 *  clientId    ->      版本号
 * queue_version
 *  clientId    ->      版本号
 * subscription_version
 *  clientId-topic  ->  版本号
 * retain_version
 *  topic       ->      版本号
 *
 * // TODO: 2022/8/2 暂时没有考虑溢出的情况,后续再来处理
 */
public final class RedisVersionService extends AbstractLifecycle implements VersionService {

    private RedissonService redissonService;

    public RedisVersionService() {
    }

    @Override
    public boolean isExpired(String key, String field, long tick) {
        String script =
            "if (redis.call('hexists', KEYS[1], ARGV[1]) == 0) then " +
                "return 1;" +
            "else " +
                "return redis.call('hget', KEYS[1], ARGV[1]) > ARGV[2];" +
            "end; ";
        return redissonService.executeBoolean(script,
                Collections.singletonList(key),
                field, tick);
    }

    @Override
    public long tick(String key, String field) {
        String script =
                "return redis.call('hincrby', KEYS[1], ARGV[1], 1);";
        return redissonService.executeLong(script,
                Collections.singletonList(key),
                field);
    }

    @Override
    public void start() throws Exception {
        AssertUtils.isTrue(redissonService != null, "redissonService MUST be set");
        super.start();
    }

    public void setRedissonService(RedissonService redissonService) {
        this.redissonService = redissonService;
    }
}
