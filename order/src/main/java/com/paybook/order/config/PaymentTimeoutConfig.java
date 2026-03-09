package com.paybook.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.payment")
public record PaymentTimeoutConfig(int timeoutMinutes) {
}
