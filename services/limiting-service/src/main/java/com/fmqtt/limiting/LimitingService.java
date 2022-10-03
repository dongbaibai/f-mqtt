package com.fmqtt.limiting;

import com.fmqtt.common.lifecycle.Lifecycle;

public interface LimitingService extends Lifecycle {

    boolean acquire(String resource) throws Exception;

    void registerResource(String resource, Object object, double qps);

    String resource(Object object);


}
