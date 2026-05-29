package com.happyim.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关限流规则
 */
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initRules() {
        Set<ApiDefinition> apis = new HashSet<>();
        Set<GatewayFlowRule> rules = new HashSet<>();

        // ========== API 分组定义 ==========

        // 短信发送接口
        ApiDefinition sendCodeApi = new ApiDefinition("send_code")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/auth/send-code"));
                }});
        apis.add(sendCodeApi);

        // 登录接口
        ApiDefinition loginApi = new ApiDefinition("login")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/auth/login"));
                }});
        apis.add(loginApi);

        // 注册接口
        ApiDefinition registerApi = new ApiDefinition("register")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/auth/register"));
                }});
        apis.add(registerApi);

        // 全局接口（所有 /api/**）
        ApiDefinition globalApi = new ApiDefinition("global")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        apis.add(globalApi);

        // ========== 限流规则 ==========

        // 短信轰炸：60s 内同 IP 最多 3 次
        rules.add(new GatewayFlowRule("send_code")
                .setCount(3).setIntervalSec(60)
                .setControlBehavior(SentinelGatewayConstants.CONTROL_BEHAVIOR_DEFAULT));

        // 暴力破解：60s 内同 IP 最多 10 次登录
        rules.add(new GatewayFlowRule("login")
                .setCount(10).setIntervalSec(60)
                .setControlBehavior(SentinelGatewayConstants.CONTROL_BEHAVIOR_DEFAULT));

        // 批量注册：60s 内同 IP 最多 3 次
        rules.add(new GatewayFlowRule("register")
                .setCount(3).setIntervalSec(60)
                .setControlBehavior(SentinelGatewayConstants.CONTROL_BEHAVIOR_DEFAULT));

        // 全局保护：每秒最多 500 请求
        rules.add(new GatewayFlowRule("global")
                .setCount(500).setIntervalSec(1)
                .setControlBehavior(SentinelGatewayConstants.CONTROL_BEHAVIOR_DEFAULT));

        GatewayApiDefinitionManager.loadApiDefinitions(apis);
        GatewayRuleManager.loadRules(rules);
    }
}
