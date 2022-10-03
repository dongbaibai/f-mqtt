package com.fmqtt.common.lifecycle;

public abstract class AbstractLifecycle implements Lifecycle {

    private volatile boolean running;

    public void start() throws Exception {
        running = true;
    }

    public void shutdown() throws Exception {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

}
