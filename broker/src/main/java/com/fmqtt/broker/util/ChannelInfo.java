package com.fmqtt.broker.util;

import com.fmqtt.session.Session;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ChannelInfo {

    private final static String USERNAME_KEY = "0";
    private final static String CLIENT_ID_KEY = "1";
    private final static String SESSION_KEY = "2";

    private static final String ATTRS = "attributes";
    private static final AttributeKey<Map<String, Object>> CHANNEL_INFO_ATTRIBUTE_KEY = AttributeKey.valueOf(ATTRS);

    private static Map<String, Object> getInfo(Channel channel) {
        Attribute<Map<String, Object>> infoAttribute = channel.attr(CHANNEL_INFO_ATTRIBUTE_KEY);
        if (infoAttribute.get() == null) {
            infoAttribute.setIfAbsent(new ConcurrentHashMap<>(8));
        }
        return infoAttribute.get();
    }

    public static void session(Channel channel, Session session) {
        getInfo(channel).put(SESSION_KEY, session);
    }

    public static Session session(Channel channel) {
        return (Session) getInfo(channel).get(SESSION_KEY);
    }

    public static void username(Channel channel, String username) {
        getInfo(channel).put(USERNAME_KEY, username);
    }

    public static String username(Channel channel) {
        return (String) getInfo(channel).get(USERNAME_KEY);
    }

    public static void clientId(Channel channel, String clientId) {
        getInfo(channel).put(CLIENT_ID_KEY, clientId);
    }

    public static String clientId(Channel channel) {
        return (String) getInfo(channel).get(CLIENT_ID_KEY);
    }

}
