package com.fmqtt.plugin;

import com.fmqtt.common.events.DisconnectType;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.subscription.Subscription;
import com.fmqtt.common.util.ExecutorServiceUtils;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class DefaultPluginService extends AbstractLifecycle implements PluginService {

    private final static Logger log = LoggerFactory.getLogger(DefaultPluginService.class);

    private final static List<Plugin> PLUGINS = Lists.newArrayList();
    private ExecutorService executorService;

    @Override
    public void start() throws Exception {
        executorService = ExecutorServiceUtils.defaultExecutorService("plugin-service-thread");
        super.start();
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        executorService.shutdown();
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public void connect(String clientId, String username) {
        if (!isRunning()) {
            return;
        }
        executorService.execute(() ->
                PLUGINS.forEach(p -> p.connect(clientId, username)));
    }

    public void disconnect(String clientId, String username, DisconnectType type) {
        if (!isRunning()) {
            return;
        }
        executorService.execute(() ->
                PLUGINS.forEach(p -> p.disconnect(clientId, username, type)));
    }

    @Override
    public void subscribe(String clientId, String username, Subscription subscription) {
        if (!isRunning()) {
            return;
        }
        executorService.execute(() ->
                PLUGINS.forEach(p -> p.subscribe(clientId, username, subscription)));
    }

    @Override
    public void unsubscribe(String clientId, String username, String topic) {
        if (!isRunning()) {
            return;
        }
        executorService.execute(() ->
                PLUGINS.forEach(p -> p.unsubscribe(clientId, username, topic)));
    }

    @Override
    public void addPlugin(Plugin plugin) {
        log.info("Add plugin name:[{}]", plugin.name());
        PLUGINS.add(plugin);
    }

    @Override
    public void removePlugin(String name) {
        log.info("Remove plugin name:[{}]", name);
        PLUGINS.removeIf(p -> name.equals(p.name()));
    }


}
