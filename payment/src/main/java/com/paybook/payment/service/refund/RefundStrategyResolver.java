package com.paybook.payment.service.refund;

import com.paybook.core.entity.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RefundStrategyResolver {

    private final List<RefundStrategy> strategies;

    public RefundStrategy resolve(PaymentMethod method) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(method))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제수단: " + method));
    }
}
