package com.fmqtt.common.constant;

public class AllConstants {

    public final static int MQTT_3_1 = 3;
    public final static int MQTT_3_1_1 = 4;
    public final static String SYS_TOPIC = "$SYS";
    public final static String SYS_CLIENT_ID = "SYS_CLIENT_ID";
    public final static String TOPIC_DELIMITER = "/";
    public final static String PLUS_SIGN = "+";
    public final static String NUMBER_SIGN = "#";
    public final static String SESSION = "session";
    public final static String RETAIN = "retain";
    public final static String SUBSCRIPTION = "subscription";
    public final static String QUEUE_MAP = "queueMap";
    public final static String QUEUE_LIST = "queueList";
    public final static String INFLIGHT = "inflight";
    public final static String SESSION_VERSION_KEY = "session_version";
    public final static String QUEUE_VERSION_KEY = "queue_version";
    public final static String SUBSCRIPTION_VERSION_KEY = "subscription_version";
    public final static String RETAIN_VERSION_KEY = "retain_version";
    public final static String FORMAT = "%s:%s";
    public static String DIRECTORY;
    public final static String CONNECTION_RESOURCE_KEY = "connection_resource";
    public final static String DISCONNECTION_RESOURCE_KEY = "disconnection_resource";
    public final static String SUB_RESOURCE_KEY = "sub_resource";
    public final static String UNSUB_RESOURCE_KEY = "unsub_resource";
    public final static String PUB_RESOURCE_KEY = "pub_resource";
    public final static String PUBACK_RESOURCE_KEY = "puback_resource";
    public final static String PING_RESOURCE_KEY = "ping_resource";

    public static String key(String clientId, String suffix) {
        return String.format(FORMAT, clientId, suffix);
    }

    public static void setDirectory(String directory) {
        DIRECTORY = directory;
    }

}
