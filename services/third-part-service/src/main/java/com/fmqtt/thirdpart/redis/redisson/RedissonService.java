package com.fmqtt.thirdpart.redis.redisson;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import org.redisson.Redisson;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;

import java.util.List;

public class RedissonService extends AbstractLifecycle {

    private final RedissonClient redissonClient;
    private final RScript rScript;

    public RedissonService(boolean epoll) {
        Config config = new Config();
        config.setTransportMode(epoll ? TransportMode.EPOLL : TransportMode.NIO);
        config.setNettyThreads(0);
        config.setThreads(0);
        config.useSingleServer().setAddress(BrokerConfig.redisAddress);
        config.setCodec(JsonJacksonCodec.INSTANCE);
        // config.setCodec(StringCodec.INSTANCE);
        redissonClient = Redisson.create(config);
        rScript = redissonClient.getScript();
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    public Boolean executeBoolean(String script, List<Object> keys, Object... values) {
        return rScript.eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.BOOLEAN, keys, values);
    }

    public Long executeLong(String script, List<Object> keys, Object... values) {
        return Long.valueOf(
                rScript.eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER, keys, values).toString());
    }

    public <T> List<T> executeList(String script, List<Object> keys, Object... values) {
        return rScript.eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.MULTI, keys, values);
    }

    public void shutdown() {
        redissonClient.shutdown();
    }

}
