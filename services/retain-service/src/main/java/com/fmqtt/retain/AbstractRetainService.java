package com.fmqtt.retain;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.common.util.AssertUtils;
import com.fmqtt.metric.MetricService;
import com.fmqtt.metric.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 考虑到本地存储带来的问题,把H2MvStore的实现直接去掉了
 */
public abstract class AbstractRetainService extends AbstractLifecycle
        implements RetainService {

    private final static Logger log = LoggerFactory.getLogger(AbstractRetainService.class);

    // 对topic加锁
    private Counter messageCount;
    protected MetricService metricService;

    @Override
    public void start() throws Exception {
        AssertUtils.isTrue(metricService != null, "metricService MUST be set");
        if (BrokerConfig.metricEnable) {
            messageCount = metricService.counter("retain.count", "$SYS/retain/count");
        }

        super.start();
    }

    protected abstract boolean saveRetain(RetainMessage retainMessage);

    protected abstract RetainMessage loadRetain(String topic);

    @Override
    public boolean addRetain(RetainMessage retainMessage) {
        if (!isRunning() || !canStore(retainMessage.getPayload().length,
                retainMessage.getTopic(), retainMessage.getSenderId())) {
            return false;
        }
        if (retainMessage.getPayload().length == 0) {
            if (messageCount != null) {
                messageCount.dec();
            }
        } else {
            if (messageCount != null) {
                messageCount.inc();
            }
        }

        return saveRetain(retainMessage);
    }

    @Override
    public RetainMessage getRetain(String topic) {
        if (!isRunning()) {
            return null;
        }
        return loadRetain(topic);
    }

    private boolean canStore(int bytes, String topic, String clientId) {
        if (bytes > BrokerConfig.retainMaxBytesPerMessage) {
            log.error("Maximum retain message bytes exceeded:[{}]", BrokerConfig.retainMaxBytesPerMessage);
        } else if (BrokerConfig.retainAllowTopics != null && !BrokerConfig.retainAllowTopics.contains(topic)) {
            log.error("Topic:[{}] is not allow to store retain message", topic);
        } else if (BrokerConfig.retainAllowClientIds != null && !BrokerConfig.retainAllowClientIds.contains(clientId)) {
            log.error("ClientId:[{}] is not allow to store retain message", clientId);
        } else {
            return true;
        }
        return false;
    }

    public void setMetricService(MetricService metricService) {
        this.metricService = metricService;
    }

}
