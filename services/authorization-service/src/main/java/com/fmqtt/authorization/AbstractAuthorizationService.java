package com.fmqtt.authorization;

import com.fmqtt.common.lifecycle.AbstractLifecycle;

public abstract class AbstractAuthorizationService extends AbstractLifecycle
        implements AuthorizationService {

    @Override
    public boolean authorize(String topic, String username, String clientId, Action action) {
        if (!isRunning()) {
            return false;
        }

        return doAuthorize(topic, username, clientId, action);
    }

    public abstract boolean doAuthorize(String topic, String username, String clientId, Action action);

}
