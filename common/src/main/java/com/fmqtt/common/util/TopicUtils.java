package com.fmqtt.common.util;

import com.fmqtt.common.config.BrokerConfig;
import com.fmqtt.common.constant.AllConstants;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public abstract class TopicUtils {

    public static boolean isValid(String topic) {
        if (StringUtils.isBlank(topic)) {
            return false;
        }
        if (topic.startsWith(AllConstants.TOPIC_DELIMITER)
                || topic.endsWith(AllConstants.TOPIC_DELIMITER)) {
            return false;
        }
        if (topic.length() > BrokerConfig.topicMaxLength) {
            return false;
        }
        if (topic.split(AllConstants.TOPIC_DELIMITER).length >
                BrokerConfig.topicMaxLevels) {
            return false;
        }
        return true;
    }

    public static String[] parseTopic(String topicFilter) {
        AssertUtils.isTrue(StringUtils.isNotBlank(topicFilter)
                , "Topic MUST be at least 1 character");
        ArrayList<String> topics = Lists.newArrayList();
        char slash = '/';
        char[] topicChars = topicFilter.toCharArray();
        int prev = 0, current = 0;
        while (current < topicChars.length) {
            int s = -1, e = -1;
            if (slash == topicChars[current]) {
                if (current == 0) {
                    s = prev;
                    e = current + 1;
                    prev++;
                } else if (current != topicChars.length - 1) {
                    s = prev;
                    e = current;
                    prev = current + 1;
                } else {
                    s = prev;
                    e = topicChars.length;
                }
            } else if (current == topicChars.length - 1) {
                s = prev;
                e = topicChars.length;
            }
            if (s != -1) {
                topics.add(topicFilter.substring(s, e));
            }
            current++;
        }
        return topics.toArray(new String[0]);
    }

    public static boolean matchTopic(String define, String request) {
        String[] defineArr = TopicUtils.parseTopic(define);
        String[] requestArr = TopicUtils.parseTopic(request);
        int i = 0;
        for (; i < defineArr.length; i++) {
            if (defineArr[i].equals(AllConstants.NUMBER_SIGN)) {
                return true;
            }
            if (defineArr[i].equals(AllConstants.PLUS_SIGN)) {
                if (i >= requestArr.length) {
                    return false;
                } else {
                    continue;
                }
            }
            if (!defineArr[i].equals(requestArr[i])) {
                return false;
            }
        }
        return i == requestArr.length;
    }

}
