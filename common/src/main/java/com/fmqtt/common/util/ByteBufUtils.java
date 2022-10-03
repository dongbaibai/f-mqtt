package com.fmqtt.common.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public abstract class ByteBufUtils {

    public static ByteBuf wrappedPayload(byte[] payload) {
        return Unpooled.copiedBuffer(payload);
    }

    public static byte[] bytes(ByteBuf payload) {
        byte[] p = new byte[payload.readableBytes()];
        payload.duplicate().readBytes(p);
        return p;
    }

}
