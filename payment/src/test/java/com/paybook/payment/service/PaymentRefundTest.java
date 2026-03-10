package com.paybook.payment.service;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentStatus;
import com.paybook.core.repository.PaymentRepository;
import com.paybook.payment.client.PgClient;
import com.paybook.payment.client.StubPgClient;
import com.paybook.payment.client.OrderServiceClient;
import com.paybook.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 환불 처리가 올바르게 동작하는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - SUCCESS 상태의 결제만 환불 가능하다.
 * - PG사에 환불 요청을 보내고, 성공 시 결제 상태가 REFUNDED로 변경된다.
 * - PENDING/FAILED 상태의 결제는 환불할 수 없다.
 *
 * 검증 목적:
 * - 정상 결제 후 환불 → 상태가 REFUNDED로 변경되는지
 * - PENDING 상태에서 환불 시도 → 예외가 발생하는지
 * - 이미 환불된 결제를 재환불 시도 → 예외가 발생하는지
 */
@DataJpaTest
@Import({PaymentService.class, StubPgClient.class})
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
@DisplayName("결제 환불")
class PaymentRefundTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("SUCCESS 결제 환불 → 상태가 REFUNDED로 변경된다")
    void successPayment_refunded() {
        PaymentEntity payment = paymentService.processPayment("ORD-001", 10000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        PaymentEntity refunded = paymentService.refundPayment("ORD-001");
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("PENDING 결제 환불 시도 → 환불 불가 예외 발생")
    void pendingPayment_cannotRefund() {
        paymentRepository.save(new PaymentEntity("PAY-PENDING", "ORD-PENDING", 10000));

        assertThatThrownBy(() -> paymentService.refundPayment("ORD-PENDING"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("환불 가능한 상태가 아닙니다");
    }

    @Test
    @DisplayName("이미 환불된 결제 재환불 시도 → 환불 불가 예외 발생")
    void refundedPayment_cannotRefundAgain() {
        paymentService.processPayment("ORD-002", 10000);
        paymentService.refundPayment("ORD-002");

        assertThatThrownBy(() -> paymentService.refundPayment("ORD-002"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("환불 가능한 상태가 아닙니다");
    }

    @Test
    @DisplayName("존재하지 않는 주문의 환불 시도 → 예외 발생")
    void nonExistentOrder_throws() {
        assertThatThrownBy(() -> paymentService.refundPayment("ORD-NONEXISTENT"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("결제 내역을 찾을 수 없습니다");
    }
}
