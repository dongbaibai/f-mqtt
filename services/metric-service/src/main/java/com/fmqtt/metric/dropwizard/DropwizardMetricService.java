package com.fmqtt.metric.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.metric.MetricService;
import com.fmqtt.metric.metrics.Counter;
import com.fmqtt.metric.metrics.Metric;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DropwizardMetricService extends ScheduledReporter implements MetricService {

    private volatile boolean running = false;
    private final MetricRegistry metricRegistry;
    private final static Map<String, Metric> METRIC_MAP = Maps.newConcurrentMap();
    private final Consumer<Serializable> metricConsumer;

    public DropwizardMetricService(MetricRegistry metricRegistry, Consumer<Serializable> metricConsumer) {
        super(metricRegistry, "dropwizard-reporter",
                MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
        this.metricRegistry = metricRegistry;
        this.metricConsumer = metricConsumer;
    }

    @Override
    public Counter counter(String name, String topic) {
        AssertUtils.isTrue(!METRIC_MAP.containsKey(name), "Duplicate metric name:" + name);
        com.codahale.metrics.Counter counter = metricRegistry.counter(name);
        DropwizardCounter dropwizardCounter = new DropwizardCounter(counter, topic);
        METRIC_MAP.put(name, dropwizardCounter);
        return dropwizardCounter;
    }

    @Override
    public <T> com.fmqtt.metric.dropwizard.Gauge<T> gauge(String name, String topic, Supplier<T> supplier) {
        AssertUtils.isTrue(!METRIC_MAP.containsKey(name), "Duplicate metric name:" + name);
        Gauge<T> gauge = metricRegistry.gauge(name, () -> supplier::get);
        DropwizardGauge<T> dropwizardGauge = new DropwizardGauge<>(gauge, topic);
        METRIC_MAP.put(name, dropwizardGauge);
        return dropwizardGauge;
    }

    @Override
    public void start() {
        if (BrokerConfig.metricEnable) {
            super.start(BrokerConfig.metricReportIntervalMills, java.util.concurrent.TimeUnit.MILLISECONDS);
            running = true;
        }
    }

    @Override
    public void shutdown() throws Exception {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, com.codahale.metrics.Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        if (!isRunning()) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        System.out.println(sdf.format(new Date()));

        histograms.forEach((k, v) -> {
        });
        meters.forEach((k, v) -> {
        });
        gauges.forEach((k, v) -> {
            metricConsumer.accept(METRIC_MAP.get(k));
        });
        counters.forEach((k, v) -> {
            metricConsumer.accept(METRIC_MAP.get(k));
        });
    }
}
