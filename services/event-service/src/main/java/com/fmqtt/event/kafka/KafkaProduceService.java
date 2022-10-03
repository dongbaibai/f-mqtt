package com.fmqtt.event.kafka;

import com.alibaba.fastjson.JSON;
import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.events.Event;
import com.fmqtt.common.util.AssertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;

public class KafkaProduceService {

    private final static Logger log = LoggerFactory.getLogger(KafkaProduceService.class);
    private KafkaProducer<String, String> kafkaProducer;

    public KafkaProduceService() {
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaServer)
                , BrokerConfig.EVENT_KAFKA_SERVERS + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaProducerAck)
                , BrokerConfig.EVENT_KAFKA_PRODUCER_ACK + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaKeySerializer)
                , BrokerConfig.EVENT_KAFKA_KEY_SERIALIZER + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaValueSerializer)
                , BrokerConfig.EVENT_KAFKA_VALUE_SERIALIZER + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaConnectTopic)
                , BrokerConfig.EVENT_KAFKA_PRODUCER_CONNECT_TOPIC + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaDisconnectTopic)
                , BrokerConfig.EVENT_KAFKA_PRODUCER_DISCONNECT_TOPIC + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaSubscribeTopic)
                , BrokerConfig.EVENT_KAFKA_PRODUCER_SUBSCRIBE_TOPIC + " MUST be set");
        AssertUtils.isTrue(StringUtils.isNotBlank(BrokerConfig.eventKafkaUnsubscribeTopic)
                , BrokerConfig.EVENT_KAFKA_PRODUCER_UNSUBSCRIBE_TOPIC + " MUST be set");

        Properties props = new Properties();
        props.put("bootstrap.servers", BrokerConfig.eventKafkaServer);
        props.put("acks", BrokerConfig.eventKafkaProducerAck);
        props.put("key.serializer", BrokerConfig.eventKafkaKeySerializer);
        props.put("value.serializer", BrokerConfig.eventKafkaValueSerializer);
        props.put("max.block.ms", 3000);
        kafkaProducer = new KafkaProducer<>(props);
    }

    public boolean send(String topic, Event event) {
        Future<RecordMetadata> future = this.kafkaProducer.send(new ProducerRecord<>(topic, JSON.toJSONString(event)));
        try {
            future.get();
            return true;
        } catch (Exception e) {
            log.error("KafkaProduceService send error, topic:[{}], clientId:[{}]"
                    , topic, event.getClientId(), e);
            return false;
        }
    }

    public void shutdown() {
        kafkaProducer.close();
    }

}
