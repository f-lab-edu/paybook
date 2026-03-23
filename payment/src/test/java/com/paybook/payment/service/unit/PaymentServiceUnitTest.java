package com.paybook.payment.service.unit;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentStatus;
import com.paybook.core.repository.PaymentRepository;
import com.paybook.payment.client.OrderServiceClient;
import com.paybook.payment.client.PgClient;
import com.paybook.payment.client.PgClient.PgPaymentResult;
import com.paybook.payment.client.PgClient.PgRefundResult;
import com.paybook.payment.exception.PaymentException;
import com.paybook.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PaymentService 순수 유닛 테스트 (런던 학파).
 *
 * Spring 컨텍스트 없이, 모든 의존성을 Mock으로 대체하여
 * PaymentService의 모든 분기 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 유닛 테스트")
class PaymentServiceUnitTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PgClient pgClient;

    @Mock
    private OrderServiceClient orderServiceClient;

    // ════════════════════════════════════════
    // processPayment — PG 결과 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("processPayment: PG 결제 결과 분기")
    class ProcessPayment {

        @Test
        @DisplayName("PG 성공 → SUCCESS 상태, confirmOrder 호출")
        void pgSuccess_marksSuccessAndConfirms() {
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(pgClient.requestPayment(anyString(), eq(10000)))
                    .willReturn(new PgPaymentResult(true, "PG-TX-001", null));

            PaymentEntity result = paymentService.processPayment("ORD-001", 10000);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(result.getPgTransactionId()).isEqualTo("PG-TX-001");
            verify(orderServiceClient).confirmOrder("ORD-001");
            verify(orderServiceClient, never()).markPaymentFailed(anyString());
        }

        @Test
        @DisplayName("PG 실패 → FAILED 상태, markPaymentFailed 호출")
        void pgFailure_marksFailedAndNotifies() {
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(pgClient.requestPayment(anyString(), eq(10000)))
                    .willReturn(new PgPaymentResult(false, null, "잔액 부족"));

            PaymentEntity result = paymentService.processPayment("ORD-001", 10000);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getPgTransactionId()).isNull();
            verify(orderServiceClient).markPaymentFailed("ORD-001");
            verify(orderServiceClient, never()).confirmOrder(anyString());
        }

        @Test
        @DisplayName("결제 엔티티가 DB에 저장된다")
        void savesPaymentEntity() {
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(pgClient.requestPayment(anyString(), anyInt()))
                    .willReturn(new PgPaymentResult(true, "PG-TX", null));

            paymentService.processPayment("ORD-001", 5000);

            verify(paymentRepository).save(any(PaymentEntity.class));
        }

        @Test
        @DisplayName("PG 호출 전에 저장이 먼저 호출된다 (PENDING 상태로)")
        void saveBeforePgCall() {
            given(paymentRepository.save(any())).willAnswer(inv -> {
                PaymentEntity saved = inv.getArgument(0);
                assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
                return saved;
            });
            given(pgClient.requestPayment(anyString(), anyInt()))
                    .willReturn(new PgPaymentResult(true, "PG-TX", null));

            paymentService.processPayment("ORD-001", 5000);

            verify(paymentRepository).save(any());
        }

        @Test
        @DisplayName("paymentId가 PAY- 접두어로 생성된다")
        void paymentIdStartsWithPrefix() {
            given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(pgClient.requestPayment(anyString(), anyInt()))
                    .willReturn(new PgPaymentResult(true, "PG-TX", null));

            PaymentEntity result = paymentService.processPayment("ORD-001", 5000);

            assertThat(result.getPaymentId()).startsWith("PAY-");
        }
    }

    // ════════════════════════════════════════
    // refundPayment — 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("refundPayment: 검증")
    class RefundPayment_Validation {

        @Test
        @DisplayName("결제 없음 → PAYMENT_NOT_FOUND, PG 환불 호출까지 가지 않는다")
        void paymentNotFound_stopsEarly() {
            given(paymentRepository.findByOrderId("ORD-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refundPayment("ORD-NONE"))
                    .isInstanceOf(PaymentException.class)
                    .satisfies(ex -> assertThat(((PaymentException) ex).getCode())
                            .isEqualTo("PAYMENT_NOT_FOUND"));

            verify(pgClient, never()).requestRefund(anyString(), anyInt());
        }

        @Test
        @DisplayName("PENDING 상태 → NOT_REFUNDABLE, PG 환불 호출까지 가지 않는다")
        void pendingStatus_notRefundable() {
            PaymentEntity payment = new PaymentEntity("PAY-001", "ORD-001", 10000);

            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment("ORD-001"))
                    .isInstanceOf(PaymentException.class)
                    .satisfies(ex -> assertThat(((PaymentException) ex).getCode())
                            .isEqualTo("NOT_REFUNDABLE"));

            verify(pgClient, never()).requestRefund(anyString(), anyInt());
        }

        @Test
        @DisplayName("FAILED 상태 → NOT_REFUNDABLE")
        void failedStatus_notRefundable() {
            PaymentEntity payment = new PaymentEntity("PAY-001", "ORD-001", 10000);
            payment.markFailed();

            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment("ORD-001"))
                    .isInstanceOf(PaymentException.class)
                    .satisfies(ex -> assertThat(((PaymentException) ex).getCode())
                            .isEqualTo("NOT_REFUNDABLE"));
        }

        @Test
        @DisplayName("REFUNDED 상태 → NOT_REFUNDABLE")
        void refundedStatus_notRefundable() {
            PaymentEntity payment = new PaymentEntity("PAY-001", "ORD-001", 10000);
            payment.markSuccess("PG-TX-001");
            payment.markRefunded();

            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment("ORD-001"))
                    .isInstanceOf(PaymentException.class)
                    .satisfies(ex -> assertThat(((PaymentException) ex).getCode())
                            .isEqualTo("NOT_REFUNDABLE"));
        }
    }

    // ════════════════════════════════════════
    // refundPayment — PG 환불 결과 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("refundPayment: PG 환불 결과")
    class RefundPayment_PgResult {

        @Test
        @DisplayName("PG 환불 성공 → REFUNDED 상태")
        void pgRefundSuccess_marksRefunded() {
            PaymentEntity payment = successPayment();
            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));
            given(pgClient.requestRefund("PG-TX-001", 10000))
                    .willReturn(new PgRefundResult(true, "REFUND-001", null));

            PaymentEntity result = paymentService.refundPayment("ORD-001");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("PG 환불 실패 → PG_REFUND_FAILED, 상태 변경 없음")
        void pgRefundFailure_throwsAndKeepsStatus() {
            PaymentEntity payment = successPayment();
            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));
            given(pgClient.requestRefund("PG-TX-001", 10000))
                    .willReturn(new PgRefundResult(false, null, "PG 시스템 오류"));

            assertThatThrownBy(() -> paymentService.refundPayment("ORD-001"))
                    .isInstanceOf(PaymentException.class)
                    .satisfies(ex -> assertThat(((PaymentException) ex).getCode())
                            .isEqualTo("PG_REFUND_FAILED"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("PG 환불 시 pgTransactionId와 금액이 전달된다")
        void passesCorrectParams() {
            PaymentEntity payment = successPayment();
            given(paymentRepository.findByOrderId("ORD-001")).willReturn(Optional.of(payment));
            given(pgClient.requestRefund("PG-TX-001", 10000))
                    .willReturn(new PgRefundResult(true, "REFUND-001", null));

            paymentService.refundPayment("ORD-001");

            verify(pgClient).requestRefund("PG-TX-001", 10000);
        }
    }

    // ════════════════════════════════════════
    // 헬퍼
    // ════════════════════════════════════════

    private static PaymentEntity successPayment() {
        PaymentEntity payment = new PaymentEntity("PAY-001", "ORD-001", 10000);
        payment.markSuccess("PG-TX-001");
        return payment;
    }
}
