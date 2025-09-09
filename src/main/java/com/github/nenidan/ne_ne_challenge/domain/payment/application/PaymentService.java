package com.github.nenidan.ne_ne_challenge.domain.payment.application;

import java.util.List;

import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentCancelRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentConfirmRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentPrepareRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.request.PaymentSearchRequest;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentCancelResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentConfirmResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentPrepareResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentSearchResponse;
import com.github.nenidan.ne_ne_challenge.domain.payment.presentation.dto.response.PaymentStatisticsResponse;
import com.github.nenidan.ne_ne_challenge.global.dto.CursorResponse;

public interface PaymentService {

    /**
     * 결제 준비
     * @param userId 사용자 ID
     * @param request 결제 준비 요청
     * @return 결제 준비 응답
     */
    PaymentPrepareResponse preparePayment(Long userId, PaymentPrepareRequest request);

    /**
     * 결제 승인 및 포인트 충전
     * @param userId 사용자 ID
     * @param request 결제 승인 요청
     * @return 결제 승인 응답
     */
    PaymentConfirmResponse confirmAndChargePoint(Long userId, PaymentConfirmRequest request);

    /**
     * 결제 취소
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param request 결제 취소 요청
     * @return 결제 취소 응답
     */
    PaymentCancelResponse cancelPayment(Long userId, String orderId, PaymentCancelRequest request);

    /**
     * 결제 내역 조회
     * @param userId 사용자 ID
     * @param request 검색 조건
     * @return 결제 내역 목록
     */
    CursorResponse<PaymentSearchResponse, Long> searchMyPayments(Long userId, PaymentSearchRequest request);

    // ================================ 통계용 ================================

    /**
     * 전체 결제 통계 조회 (관리자/모니터링용)
     */
    List<PaymentStatisticsResponse> getAllPayments();
}
