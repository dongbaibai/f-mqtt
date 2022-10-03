package com.fmqtt.event.kafka;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.events.Event;
import com.fmqtt.common.events.EventType;
import com.fmqtt.event.AbstractEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: 2022/7/6 消费要控制拉取条数，以及处理的线程池，以及是否偏移量的提交方式等，生产者无需过多处理
public class KafkaEventService extends AbstractEventService {

    private final static Logger log = LoggerFactory.getLogger(KafkaEventService.class);

    private final KafkaSyncService kafkaSyncService;
    private final KafkaPersistService kafkaPersistService;
    private final KafkaProduceService kafkaProduceService;

    public KafkaEventService() {
        this.kafkaSyncService = new KafkaSyncService();
        this.kafkaPersistService = new KafkaPersistService();
        this.kafkaProduceService = new KafkaProduceService();
    }

    @Override
    public void start() throws Exception {
        kafkaSyncService.start(this::handleSyncEvent);
        kafkaPersistService.start(this::handlePersistEvent);
        super.start();
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        kafkaProduceService.shutdown();
        kafkaSyncService.shutdown();
        kafkaPersistService.shutdown();
    }

    @Override
    public boolean publishConnect(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.CONNECT, clientId, tick, body);
            log.info("KafkaEventService publishConnect clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaConnectTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishDisconnect(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.DISCONNECT, clientId, tick, body);
            log.info("KafkaEventService publishDisconnect clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaDisconnectTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishSubscribe(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.SUBSCRIBE, clientId, tick, body);
            log.info("KafkaEventService publishSubscribe clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaSubscribeTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishUnsubscribe(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.UNSUBSCRIBE, clientId, tick, body);
            log.info("KafkaEventService publishUnsubscribe clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaUnsubscribeTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishAddRetain(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.ADD_RETAIN, clientId, tick, body);
            log.info("KafkaEventService publishAddRetain clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaAddRetainTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishAddQueue(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.ADD_QUEUE, clientId, tick, body);
            log.info("KafkaEventService publishAddQueue clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaAddQueueTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishRemoveQueue(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.REMOVE_QUEUE, clientId, tick, body);
            log.info("KafkaEventService publishRemoveQueue clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaRemoveQueueTopic, event);
        }
        return false;
    }

    @Override
    public boolean publishCleanQueue(String clientId, long tick, byte[] body) {
        if (isRunning()) {
            Event event = new Event(EventType.CLEAN_QUEUE, clientId, tick, body);
            log.info("KafkaEventService publishCleanQueue clientId:[{}], tick:[{}]", clientId, tick);
            return kafkaProduceService.send(BrokerConfig.eventKafkaCleanQueueTopic, event);
        }
        return false;
    }

}
