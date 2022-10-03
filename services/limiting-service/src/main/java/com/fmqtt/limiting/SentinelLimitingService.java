package com.fmqtt.limiting;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fmqtt.common.lifecycle.AbstractLifecycle;
import com.fmqtt.common.util.AssertUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SentinelLimitingService extends AbstractLifecycle implements LimitingService {

    private final static Logger log = LoggerFactory.getLogger(SentinelLimitingService.class);

    private final static Map<Object, String> RESOURCES_MAP = Maps.newConcurrentMap();
    private final static List<FlowRule> RULES = Lists.newArrayList();

    @Override
    public void start() throws Exception {
        FlowRuleManager.loadRules(RULES);
        super.start();
    }

    @Override
    public boolean acquire(String resource) throws Exception {
        if (!isRunning()) {
            return false;
        }
        try (Entry entry = SphU.entry(resource)) {
            return true;
        } catch (BlockException blockException) {
            if (blockException instanceof FlowException) {
                log.warn("Fail to acquire resource:[{}]", resource);
                return false;
            }
            throw blockException;
        }
    }

    @Override
    public void registerResource(String resource, Object object, double qps) {
        AssertUtils.isTrue(!RESOURCES_MAP.containsKey(resource), "Duplicate resource:" + resource);
        RESOURCES_MAP.put(object, resource);
        FlowRule rule = new FlowRule();
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        RULES.add(rule);
    }

    @Override
    public String resource(Object object) {
        return RESOURCES_MAP.get(object);
    }

}
