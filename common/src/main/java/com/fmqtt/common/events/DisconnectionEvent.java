package com.fmqtt.common.events;

public class DisconnectionEvent {

    private DisconnectType type;

    private String username;

    private DisconnectionEvent(DisconnectType type, String username) {
        this.type = type;
        this.username = username;
    }

    public static DisconnectionEvent buildEvent(DisconnectType type, String username) {
        return new DisconnectionEvent(type, username);
    }

    public DisconnectType getType() {
        return type;
    }

    public void setType(DisconnectType type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
