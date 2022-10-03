package com.fmqtt.common.util;

import com.fmqtt.common.RequestTask;
import com.fmqtt.common.config.BrokerConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ExecutorServiceUtils {

    private final static Logger log = LoggerFactory.getLogger(ExecutorServiceUtils.class);

    public final static ExecutorService COMMON;
    private final static ScheduledExecutorService scheduledExecutorService;

    static {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("expired-request-finalizer").build());
        COMMON = defaultExecutorService("common-executor-thread");
    }

    public static ExecutorService defaultExecutorService(String namePrefix) {
        int core = Runtime.getRuntime().availableProcessors();
        int max = core * 2;
        return new FThreadPoolExecutor(core, max,
                60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(BrokerConfig.executorQueueSize),
                new ThreadFactoryBuilder().setNameFormat(namePrefix).build(),
                (r, executor) -> log.error("[{}] overwork", namePrefix));
    }

    public static class FThreadPoolExecutor extends ThreadPoolExecutor {

        public FThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                   long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                   ThreadFactory threadFactory,
                                   RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
            scheduledExecutorService.scheduleWithFixedDelay(
                    new ExpiredRequestFinalizer(workQueue),
                    BrokerConfig.expiredRequestCheckIntervalMills, BrokerConfig.expiredRequestCheckIntervalMills,
                    TimeUnit.MILLISECONDS);
        }

    }

    public static class ExpiredRequestFinalizer implements Runnable {

        private BlockingQueue<Runnable> queue;

        public ExpiredRequestFinalizer(BlockingQueue<Runnable> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            while (!queue.isEmpty()) {
                try {
                    final Runnable runnable = queue.poll(0, TimeUnit.SECONDS);
                    if (runnable == null) {
                        break;
                    }
                    RequestTask requestTask = RequestTask.cast(runnable);
                    if (requestTask == null || requestTask.isStopRun()) {
                        break;
                    }
                    final long behind = System.currentTimeMillis() - requestTask.getCreateTimestamp();
                    if (behind > BrokerConfig.executorMaxWaitTimeMills) {
                        requestTask.setStopRun(true);
                        requestTask.returnResponse();
                    }
                } catch (InterruptedException ignore) {}
            }
        }
    }

    public static Boolean getBoolean(CompletableFuture<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Fail to getResult", e);
        }
        return false;
    }

}
