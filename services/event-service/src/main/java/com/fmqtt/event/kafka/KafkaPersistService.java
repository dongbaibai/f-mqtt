package com.fmqtt.event.kafka;

import com.alibaba.fastjson.JSON;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.events.Event;
import com.fmqtt.common.util.AssertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 消费Kafka中事件,便于数据持久化操作「消费者组」
 */
public class KafkaPersistService {

    private final static Logger log = LoggerFactory.getLogger(KafkaPersistService.class);
    // 解决多线程操作KafkaConsumer的情况,ConcurrentModificationException
    private final ReentrantLock lock = new ReentrantLock();
    private final static String GROUP_ID = "event-persistence-group";
    private volatile boolean running = false;

    private KafkaConsumer<String, String> kafkaConsumer;

    public KafkaPersistService() {
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaServer)
                , BrokerConfig.EVENT_KAFKA_SERVERS + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaKeyDeserializer)
                , BrokerConfig.EVENT_KAFKA_KEY_DESERIALIZER + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaValueDeserializer)
                , BrokerConfig.EVENT_KAFKA_VALUE_DESERIALIZER + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaConsumerPersistSubscribeTopics)
                , BrokerConfig.EVENT_KAFKA_CONSUMER_PERSIST_SUBSCRIBE_TOPICS + "MUST be set");

        Properties props = new Properties();
        props.put("bootstrap.servers", BrokerConfig.eventKafkaServer);
        props.put("key.deserializer", BrokerConfig.eventKafkaKeyDeserializer);
        props.put("value.deserializer", BrokerConfig.eventKafkaValueDeserializer);
        // 广播
        props.put("group.id", GROUP_ID);
//        props.put("enable.auto.commit", "false");
//        props.put("auto.offset.reset", "earliest");
        this.kafkaConsumer = new KafkaConsumer<>(props);
    }

    public void start(Consumer<Event> eventConsumer) {
        log.info("KafkaConsumeService subscribe topics:[{}]", BrokerConfig.eventKafkaConsumerPersistSubscribeTopics);
        // We can group consumers into groups
        kafkaConsumer.subscribe(Arrays.stream(BrokerConfig.eventKafkaConsumerPersistSubscribeTopics.split(","))
                .map(String::trim).collect(Collectors.toList()));
        running = true;
        new Thread(() -> {
            try {
                while (running) {
                    lock.lock();
                    try {
                        for (ConsumerRecord<String, String> record :
                                kafkaConsumer.poll(Duration.ofMillis(BrokerConfig.eventKafkaConsumerPollTimeoutMills))) {
                            eventConsumer.accept(JSON.parseObject(record.value(), Event.class));
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Throwable ignore) {
                log.error("Error occurs to KafkaPollMessageThread", ignore);
            }
        }, "KafkaPollMessageThread").start();
    }

    public void shutdown() {
        // 优化,降低争抢
        running = false;
        lock.lock();
        try {
            kafkaConsumer.close();
        } finally {
            lock.unlock();
        }
    }
}
