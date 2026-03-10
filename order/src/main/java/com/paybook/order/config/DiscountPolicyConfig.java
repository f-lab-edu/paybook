package com.paybook.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.discount")
public record DiscountPolicyConfig(int maxDiscountPercent, int minPgPaymentPercent) {
}
