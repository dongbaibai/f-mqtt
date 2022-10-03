package com.fmqtt.retain;

import com.fmqtt.common.lifecycle.Lifecycle;
import com.fmqtt.common.message.RetainMessage;
import com.fmqtt.metric.MetricService;

public interface RetainService extends Lifecycle {

    boolean addRetain(RetainMessage retainMessage);

    RetainMessage getRetain(String topic);

    void setMetricService(MetricService metricService);

}
