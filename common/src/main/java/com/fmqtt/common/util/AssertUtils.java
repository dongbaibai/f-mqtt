package com.fmqtt.common.util;


import com.fmqtt.common.exception.AssertException;

public abstract class AssertUtils {

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertException(message);
        }
    }

}
