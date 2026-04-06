package com.paybook.settlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.policy")
public record SettlementPolicyConfig(
        int defaultCommissionRate,
        int confirmDelayDays
) {
}
