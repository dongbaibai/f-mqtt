package com.fmqtt.common.lifecycle;

public interface Lifecycle {

    void start() throws Exception;

    void shutdown() throws Exception;

    boolean isRunning();

}
