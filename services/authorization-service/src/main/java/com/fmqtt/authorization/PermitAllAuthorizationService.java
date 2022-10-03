package com.fmqtt.authorization;

/**
 * Permit All
 */
public class PermitAllAuthorizationService extends AbstractAuthorizationService {

    @Override
    public boolean doAuthorize(String topic, String username, String clientId, Action action) {
        return true;
    }

}
