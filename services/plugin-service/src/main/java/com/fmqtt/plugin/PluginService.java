package com.fmqtt.plugin;

import com.fmqtt.common.lifecycle.Lifecycle;

public interface PluginService extends Plugin, Lifecycle {

    void addPlugin(Plugin plugin);

    void removePlugin(String name);

}
