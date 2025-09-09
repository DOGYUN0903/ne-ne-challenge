package com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response;

import java.time.LocalDateTime;

import com.github.nenidan.ne_ne_challenge.domain.payment.domain.model.Payment;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.type.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PaymentStatisticsResponse {

    private Long id;

    private Long userId;

    private int amount;

    private String paymentMethod;

    private String paymentKey;

    private String orderId;

    private PaymentStatus status;

    private String cancelReason;

    private LocalDateTime requestedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime failedAt;

    private LocalDateTime canceledAt;

    public static PaymentStatisticsResponse toDto(Payment payment) {
        return new PaymentStatisticsResponse(
            payment.getId(),
            payment.getUserId(),
            payment.getAmount().getValue(),
            payment.getPaymentMethod(),
            payment.getPaymentKey() != null ? payment.getPaymentKey().getValue() : null,
            payment.getOrderId().getValue(),
            payment.getStatus(),
            payment.getCancelReason(),
            payment.getRequestedAt(),
            payment.getApprovedAt(),
            payment.getFailedAt(),
            payment.getCanceledAt()
        );
    }
}
