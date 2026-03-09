package com.paybook.order.service;

import com.paybook.order.config.PaymentTimeoutConfig;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentTimeoutConfig paymentTimeoutConfig;

    @Scheduled(fixedDelayString = "${order.payment.timeout-check-interval:60000}")
    public void cancelTimedOutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentTimeoutConfig.timeoutMinutes());
        List<OrderEntity> timedOutOrders = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff);

        timedOutOrders.forEach(order -> {
            try {
                orderService.cancelOrder(order.getOrderId());
                log.info("결제 타임아웃 주문 자동 취소: {}", order.getOrderId());
            } catch (Exception e) {
                log.error("결제 타임아웃 주문 취소 실패: {}", order.getOrderId(), e);
            }
        });
    }

    public int cancelTimedOutOrdersManually(LocalDateTime cutoff) {
        List<OrderEntity> timedOutOrders = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff);

        timedOutOrders.forEach(order -> orderService.cancelOrder(order.getOrderId()));
        return timedOutOrders.size();
    }
}
