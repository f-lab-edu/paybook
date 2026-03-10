package com.paybook.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.delivery")
public record DeliveryFeeConfig(int fee, int freeThreshold) {
}
