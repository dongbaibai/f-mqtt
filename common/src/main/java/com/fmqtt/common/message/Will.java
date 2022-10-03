package com.fmqtt.common.message;

public class Will {

    private RetainMessage message;
    private boolean retain;

    public Will() {

    }
    public Will(RetainMessage message, boolean retain) {
        this.message = message;
        this.retain = retain;
    }

    public RetainMessage getMessage() {
        return message;
    }

    public void setMessage(RetainMessage message) {
        this.message = message;
    }

    public boolean isRetain() {
        return retain;
    }

    public void setRetain(boolean retain) {
        this.retain = retain;
    }

}