package com.fmqtt.common.events;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.message.Will;

public class ConnectionEvent {

    private String clientId;
    private String username;
    private boolean cleanSession;
    private Will will;
    private String serverName;
    public ConnectionEvent(String clientId, String username,
                   boolean cleanSession, Will will, String serverName) {
        this.clientId = clientId;
        this.username = username;
        this.cleanSession = cleanSession;
        this.will = will;
        this.serverName = serverName;
    }

    public static ConnectionEvent buildEvent(String clientId, String username,
                                             boolean cleanSession, Will will) {
        return new ConnectionEvent(clientId, username, cleanSession, will, BrokerConfig.serverName);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public Will getWill() {
        return will;
    }

    public void setWill(Will will) {
        this.will = will;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
