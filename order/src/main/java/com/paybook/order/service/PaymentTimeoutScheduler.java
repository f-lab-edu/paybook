package com.paybook.order.service;

import com.paybook.order.config.PaymentTimeoutConfig;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private static final int BATCH_SIZE = 500;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentTimeoutConfig paymentTimeoutConfig;

    @Scheduled(fixedDelayString = "${order.payment.timeout-check-interval:60000}")
    public void cancelTimedOutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentTimeoutConfig.timeoutMinutes());
        int cancelled = processBatch(cutoff);
        if (cancelled > 0) {
            log.info("결제 타임아웃 주문 자동 취소: {}건", cancelled);
        }
    }

    public int cancelTimedOutOrdersManually(LocalDateTime cutoff) {
        return processBatch(cutoff);
    }

    private int processBatch(LocalDateTime cutoff) {
        int totalCancelled = 0;
        Page<OrderEntity> page;

        do {
            page = orderRepository.findByStatusAndCreatedAtBefore(
                    OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, BATCH_SIZE));

            for (OrderEntity order : page.getContent()) {
                try {
                    orderService.cancelOrder(order.getOrderId());
                    totalCancelled++;
                } catch (Exception e) {
                    log.error("결제 타임아웃 주문 취소 실패: {}", order.getOrderId(), e);
                }
            }
        } while (page.hasNext());

        return totalCancelled;
    }
}
