package com.fmqtt.authentication;

import com.fmqtt.common.lifecycle.Lifecycle;

public interface AuthenticationService extends Lifecycle {

    boolean authenticate(String username, byte[] password, String clientId);

}
