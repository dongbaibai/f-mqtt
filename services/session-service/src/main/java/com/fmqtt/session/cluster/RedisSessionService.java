package com.fmqtt.session.cluster;

import com.fmqtt.common.constant.AllConstants;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.session.AbstractSessionService;
import com.fmqtt.session.Session;
import com.fmqtt.thirdpart.redis.redisson.RedissonService;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Hash结构,session为key,clientId为field,Session为value
 */
public class RedisSessionService extends AbstractSessionService {

    private final RedissonService redissonService;

    public RedisSessionService(RedissonService redissonService) {
        this.redissonService = redissonService;
    }

    @Override
    protected Map<String, Session> findAll() {
        ConcurrentMap<String, Session> sessions = Maps.newConcurrentMap();
        sessions.putAll(redissonService.getRedissonClient().getMap(AllConstants.SESSION));
        return sessions;
    }

    @Override
    protected boolean saveSession(Session session) {
        redissonService.getRedissonClient()
                .getMap(AllConstants.SESSION)
                .put(session.getClientId(), session);
        return true;
    }

    @Override
    public void start() throws Exception {
        AssertUtils.isTrue(redissonService != null, "redissonService MUST be set");
        super.start();
    }

}
