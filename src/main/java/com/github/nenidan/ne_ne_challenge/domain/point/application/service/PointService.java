package com.github.nenidan.ne_ne_challenge.domain.point.application.service;

import java.util.List;

import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.request.PointSearchRequest;
import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.response.PointBalanceResponse;
import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.response.PointHistoryResponse;
import com.github.nenidan.ne_ne_challenge.global.dto.CursorResponse;

public interface PointService {

    // ================================ 외부 API용 ================================

    /**
     * 사용자 포인트 잔액 조회
     * @param userId 사용자 ID
     * @return 포인트 잔액
     */
    PointBalanceResponse getMyBalance(Long userId);

    /**
     * 사용자 포인트 이력 조회
     * @param userId 사용자 ID
     * @param request 검색 조건
     * @return 포인트 이력 목록
     */
    CursorResponse<PointHistoryResponse, Long> searchMyPointHistory(Long userId, PointSearchRequest request);

    // ================================ 내부 서비스용 ================================

    /**
     * 포인트 지갑 생성 (회원가입 시)
     * @param userId 사용자 ID
     */
    void createPointWallet(Long userId);

    /**
     * 포인트 충전 (결제 완료 시)
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @param reason 충전 사유
     * @param orderId 주문 ID
     */
    void chargePoint(Long userId, int amount, String reason, String orderId);

    /**
     * 포인트 증가 (챌린지 보상 등)
     * @param userId 사용자 ID
     * @param amount 증가 금액
     * @param reason 증가 사유
     */
    void increasePoint(Long userId, int amount, String reason);

    /**
     * 포인트 차감 (상품 구매, 챌린지 참가 등)
     * @param userId 사용자 ID
     * @param amount 차감 금액
     * @param reason 차감 사유
     */
    void decreasePoint(Long userId, int amount, String reason);

    /**
     * 포인트 취소 (결제 취소 시)
     * @param orderId 주문 ID
     */
    void cancelPoint(String orderId);

    /**
     * 대량 포인트 환불 (챌린지 취소 등)
     * @param userIds 대상 사용자 목록
     * @param amount 환불 금액
     */
    void refundPoints(List<Long> userIds, int amount);

    // ================================ 통계용 ================================

    /**
     * 전체 포인트 거래 내역 조회 (통계용)
     * @return 포인트 거래 내역 목록
     */
    List<PointHistoryResponse> getAllPointTransactions();
}
