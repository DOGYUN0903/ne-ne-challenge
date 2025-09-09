package com.github.nenidan.ne_ne_challenge.domain.payment.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.nenidan.ne_ne_challenge.domain.payment.application.client.TossClient;
import com.github.nenidan.ne_ne_challenge.domain.payment.application.dto.response.TossConfirmResult;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.event.PaymentCompletedEvent;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.model.Payment;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.model.vo.Money;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.model.vo.OrderId;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.repository.PaymentRepository;
import com.github.nenidan.ne_ne_challenge.domain.payment.domain.type.PaymentStatus;
import com.github.nenidan.ne_ne_challenge.domain.payment.exception.PaymentErrorCode;
import com.github.nenidan.ne_ne_challenge.domain.payment.exception.PaymentException;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentCancelRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentConfirmRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentPrepareRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentSearchRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentCancelResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentConfirmResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentPrepareResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentSearchResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentStatisticsResponse;
import com.github.nenidan.ne_ne_challenge.domain.point.application.service.PointService;
import com.github.nenidan.ne_ne_challenge.domain.user.application.UserFacade;
import com.github.nenidan.ne_ne_challenge.global.dto.CursorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final UserFacade userFacade;
    private final TossClient tossClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 준비 (토스 결제 전 주문 정보 저장)
     */
    @Transactional
    @Override
    public PaymentPrepareResponse preparePayment(Long userId, PaymentPrepareRequest request) {
        userFacade.getProfile(userId);

        Payment savedPayment = paymentRepository.save(
            Payment.createPreparePayment(userId, Money.ofPayment(request.getAmount()))
        );

        return PaymentPrepareResponse.toDto(savedPayment);
    }

    /**
     * 결제 승인 및 포인트 충전
     */
    @Transactional
    @Override
    public PaymentConfirmResponse confirmAndChargePoint(Long userId, PaymentConfirmRequest request) {
        userFacade.getProfile(userId);

        // 토스 결제 승인
        TossConfirmResult tossConfirmResult = tossClient.confirmPayment(
            request.getPaymentKey(), request.getOrderId(), request.getAmount());

        // 결제 성공 처리
        Payment payment = markAsSuccess(tossConfirmResult, request.getAmount());

        // 포인트 충전 이벤트 발행
        publishPaymentCompletedEvent(payment);

        return PaymentConfirmResponse.toDto(payment);
    }

    /**
     * 결제 취소 및 포인트 차감
     */
    @Transactional
    @Override
    public PaymentCancelResponse cancelPayment(Long userId, String orderId, PaymentCancelRequest request) {
        userFacade.getProfile(userId);

        // 취소 가능한 결제 조회 및 검증
        Payment payment = getPaymentForCancel(userId, orderId);

        // 포인트 차감 (결제로 충전된 포인트 취소)
        pointService.cancelPoint(orderId);

        // 결제 취소 처리
        payment.cancel(request.getCancelReason());
        Payment canceledPayment = paymentRepository.save(payment);

        // 토스 결제 취소
        tossClient.cancelPayment(payment.getPaymentKey().getValue(), payment.getCancelReason());

        return PaymentCancelResponse.toDto(canceledPayment);
    }

    /**
     * 결제 내역 조회 (커서 페이징)
     */
    @Transactional(readOnly = true)
    @Override
    public CursorResponse<PaymentSearchResponse, Long> searchMyPayments(Long userId, PaymentSearchRequest request) {
        userFacade.getProfile(userId);

        // 검색 조건 변환
        LocalDateTime startDate = convertToStartDateTime(request.getStartDate());
        LocalDateTime endDate = convertToEndDateTime(request.getEndDate());
        String paymentStatus = convertToStatusString(request.getStatus());

        // 결제 내역 조회 및 DTO 변환
        List<PaymentSearchResponse> responses = paymentRepository.searchPayments(
                userId, request.getCursor(), paymentStatus, startDate, endDate, request.getSize() + 1)
            .stream()
            .map(PaymentSearchResponse::toDto)
            .toList();

        return CursorResponse.of(responses, PaymentSearchResponse::getPaymentId, request.getSize());
    }

    /**
     * 전체 결제 통계 조회 (관리자/모니터링용)
     */
    @Transactional(readOnly = true)
    @Override
    public List<PaymentStatisticsResponse> getAllPayments() {
        return paymentRepository.findAll()
            .stream()
            .map(PaymentStatisticsResponse::toDto)
            .toList();
    }

    // ================================ Private 헬퍼 메서드 ================================

    /**
     * 토스 결제 승인 결과를 바탕으로 결제를 성공 상태로 변경
     */
    private Payment markAsSuccess(TossConfirmResult tossConfirmResult, int requestAmount) {
        Payment payment = getPaymentByOrderId(OrderId.of(tossConfirmResult.getOrderId()));
        Money money = Money.of(requestAmount);

        if (!payment.getAmount().equals(money)) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        payment.markAsSuccess(
            tossConfirmResult.getPaymentKey(),
            tossConfirmResult.getStatus(),
            tossConfirmResult.getMethod(),
            tossConfirmResult.getApprovedAt().toLocalDateTime()
        );

        return paymentRepository.save(payment);
    }

    /**
     * 취소 가능한 결제 조회 및 검증
     */
    private Payment getPaymentForCancel(Long userId, String orderId) {
        Payment payment = getPaymentByOrderId(OrderId.of(orderId));
        payment.validateCancelable(userId);
        return payment;
    }

    /**
     * 포인트 충전 완료 이벤트 발행
     */
    private void publishPaymentCompletedEvent(Payment payment) {
        try {
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                payment.getUserId(),
                payment.getAmount().getValue(),
                "CHARGE",
                payment.getOrderId().getValue()
            ));
        } catch (Exception e) {
            log.error("포인트 충전 이벤트 발행 실패 - 수동 처리 필요: orderId={}", payment.getOrderId(), e);
        }
    }

    private Payment getPaymentByOrderId(OrderId orderId) {
        return paymentRepository.findByOrderId(orderId.getValue())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private LocalDateTime convertToStartDateTime(LocalDate startDate) {
        return startDate != null ? startDate.atStartOfDay() : null;
    }

    private LocalDateTime convertToEndDateTime(LocalDate endDate) {
        return endDate != null ? endDate.atTime(23, 59, 59) : null;
    }

    private String convertToStatusString(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        PaymentStatus paymentStatus = PaymentStatus.of(status);
        return paymentStatus.name();
    }
}