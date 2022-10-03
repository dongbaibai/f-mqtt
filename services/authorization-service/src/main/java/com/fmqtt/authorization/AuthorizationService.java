package com.fmqtt.authorization;

import com.fmqtt.common.lifecycle.Lifecycle;

public interface AuthorizationService extends Lifecycle {

    boolean authorize(String topic, String username, String clientId, Action action);

}