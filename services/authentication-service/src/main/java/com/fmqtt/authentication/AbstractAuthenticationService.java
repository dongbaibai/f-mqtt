package com.fmqtt.authentication;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.lifecycle.AbstractLifecycle;

public abstract class AbstractAuthenticationService extends AbstractLifecycle
        implements AuthenticationService {

    @Override
    public boolean authenticate(String username, byte[] password, String clientId) {
        if (!isRunning()) {
            return false;
        }
        if (username == null) {
            return BrokerConfig.allowAnonymous;
        }
        // when username is nonNull, we need password force
        if (password == null) {
            return false;
        }

        return doAuthenticate(username, password, clientId);
    }

    public abstract boolean doAuthenticate(String username, byte[] password, String clientId);


}


