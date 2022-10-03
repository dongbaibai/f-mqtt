package com.fmqtt.metric.metrics;

import java.io.Serializable;

public interface Metric extends Serializable {

    String topic();

    String serverName();

    String jsonString();

}
