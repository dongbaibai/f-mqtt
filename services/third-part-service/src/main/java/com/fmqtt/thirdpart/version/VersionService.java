package com.fmqtt.thirdpart.version;

import com.fmqtt.common.lifecycle.Lifecycle;

/**
 * 版本服务
 */
public interface VersionService extends Lifecycle {

    /**
     * 自增并返回结果
     *
     * @return
     */
    long tick(String key, String field);

    /**
     * 判断该tick是否已经过期
     *
     * @return
     */
    boolean isExpired(String key, String field, long tick);

}
