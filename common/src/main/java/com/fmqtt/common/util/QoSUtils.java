package com.fmqtt.common.util;

public abstract class QoSUtils {

    public static int matchQoS(int pub, int sub) {
        return Math.min(pub, sub);
    }

    public static int adjustQoS(int qos) {
        return qos >= 1 ? 1 : 0;
    }

}
