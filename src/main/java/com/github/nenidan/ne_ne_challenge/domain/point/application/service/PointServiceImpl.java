package com.github.nenidan.ne_ne_challenge.domain.point.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.nenidan.ne_ne_challenge.domain.point.domain.model.Point;
import com.github.nenidan.ne_ne_challenge.domain.point.domain.model.PointTransaction;
import com.github.nenidan.ne_ne_challenge.domain.point.domain.model.PointWallet;
import com.github.nenidan.ne_ne_challenge.domain.point.domain.repository.PointRepository;
import com.github.nenidan.ne_ne_challenge.domain.point.domain.type.PointReason;
import com.github.nenidan.ne_ne_challenge.domain.point.exception.PointErrorCode;
import com.github.nenidan.ne_ne_challenge.domain.point.exception.PointException;
import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.request.PointSearchRequest;
import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.response.PointBalanceResponse;
import com.github.nenidan.ne_ne_challenge.domain.point.presentation.dto.response.PointHistoryResponse;
import com.github.nenidan.ne_ne_challenge.domain.user.application.UserFacade;
import com.github.nenidan.ne_ne_challenge.global.dto.CursorResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final UserFacade userFacade;
    private final PointRepository pointRepository;

    // ================================ 외부 API용 ================================

    /**
     * 사용자 포인트 잔액 조회
     */
    @Transactional(readOnly = true)
    @Override
    public PointBalanceResponse getMyBalance(Long userId) {
        userFacade.getProfile(userId);
        PointWallet pointWallet = getPointWalletByUserId(userId);
        return PointBalanceResponse.toDto(pointWallet);
    }

    /**
     * 사용자 포인트 이력 조회 (커서 페이징)
     */
    @Transactional(readOnly = true)
    @Override
    public CursorResponse<PointHistoryResponse, Long> searchMyPointHistory(Long userId, PointSearchRequest request) {

        userFacade.getProfile(userId);
        PointWallet pointWallet = getPointWalletByUserId(userId);

        // 검색 조건 변환
        LocalDateTime startDate = convertToStartDateTime(request.getStartDate());
        LocalDateTime endDate = convertToEndDateTime(request.getEndDate());
        PointReason pointReason = convertToPointReason(request.getReason());

        // 포인트 거래 이력 조회 및 DTO 변환
        List<PointHistoryResponse> responses = pointRepository.searchMyPointHistory(
                pointWallet.getId(), request.getCursor(), pointReason, startDate, endDate, request.getSize() + 1)
            .stream()
            .map(PointHistoryResponse::toDto)
            .toList();

        return CursorResponse.of(responses, PointHistoryResponse::getPointTransactionId, request.getSize());
    }

    // ================================ 내부 서비스용 ================================

    /**
     * 포인트 지갑 생성 (회원가입 시 호출)
     */
    @Transactional
    @Override
    public void createPointWallet(Long userId) {
        if (pointRepository.existsByUserId(userId)) {
            throw new PointException(PointErrorCode.WALLET_ALREADY_EXISTS);
        }

        PointWallet pointWallet = PointWallet.createPointWallet(userId);
        pointRepository.save(pointWallet);
    }

    /**
     * 포인트 충전 (결제 완료 시 호출)
     */
    @Transactional
    @Override
    public void chargePoint(Long userId, int amount, String reason, String orderId) {
        PointReason pointReason = PointReason.of(reason);
        PointWallet pointWallet = getPointWalletByUserId(userId);

        // 지갑 잔액 증가
        pointWallet.increase(amount);
        pointRepository.save(pointWallet);

        // 포인트 생성 (FIFO 추적용)
        Point chargePoint = Point.createChargePoint(pointWallet, amount, orderId);
        pointRepository.save(chargePoint);

        // 거래 이력 생성
        PointTransaction pointTransaction = PointTransaction.createChargeTransaction(
            pointWallet, amount, pointReason, pointReason.getDescription());
        pointRepository.save(pointTransaction);
    }

    /**
     * 포인트 증가 (챌린지 보상, 상품 환불 등)
     */
    @Transactional
    @Override
    public void increasePoint(Long userId, int amount, String reason) {
        PointReason pointReason = PointReason.of(reason);
        PointWallet pointWallet = getPointWalletByUserId(userId);

        pointWallet.increase(amount);
        pointRepository.save(pointWallet);

        PointTransaction pointTransaction = PointTransaction.createChargeTransaction(
            pointWallet, amount, pointReason, pointReason.getDescription());
        pointRepository.save(pointTransaction);
    }

    /**
     * 포인트 차감 (상품 구매, 챌린지 참가 등) - FIFO 방식
     */
    @Transactional
    @Override
    public void decreasePoint(Long userId, int amount, String reason) {
        PointReason pointReason = PointReason.of(reason);
        PointWallet pointWallet = getPointWalletByUserId(userId);

        // FIFO 방식으로 포인트 차감
        usePointsWithFifo(pointWallet, amount);

        // 거래 이력 생성
        PointTransaction pointTransaction = PointTransaction.createUsageTransaction(
            pointWallet, amount, pointReason, pointReason.getDescription());
        pointRepository.save(pointTransaction);
    }

    /**
     * 포인트 취소 (결제 취소 시 호출)
     */
    @Transactional
    @Override
    public void cancelPoint(String orderId) {
        Point point = getPointByOrderId(orderId);
        point.cancel();

        PointWallet pointWallet = point.getPointWallet();
        pointWallet.decrease(point.getAmount());

        // 취소 거래 이력 생성
        PointTransaction pointTransaction = PointTransaction.createUsageTransaction(
            pointWallet, point.getAmount(), PointReason.CHARGE_CANCEL,
            PointReason.CHARGE_CANCEL.getDescription());
        pointRepository.save(pointTransaction);
    }

    /**
     * 대량 포인트 환불 (챌린지 취소 등)
     */
    @Transactional
    @Override
    public void refundPoints(List<Long> userIds, int amount) {
        PointReason reason = PointReason.CHALLENGE_REFUND;

        for (Long userId : userIds) {
            PointWallet pointWallet = getPointWalletByUserId(userId);
            pointWallet.increase(amount);
            pointRepository.save(pointWallet);

            PointTransaction pointTransaction = PointTransaction.createChargeTransaction(
                pointWallet, amount, reason, reason.getDescription());
            pointRepository.save(pointTransaction);
        }
    }

    // ================================ 통계용 ================================

    /**
     * 전체 포인트 거래 내역 조회 (관리자/모니터링용)
     */
    @Transactional(readOnly = true)
    @Override
    public List<PointHistoryResponse> getAllPointTransactions() {
        return pointRepository.findAll()
            .stream()
            .map(PointHistoryResponse::toDto)
            .toList();
    }

    // ================================ Private 헬퍼 메서드 ================================

    /**
     * FIFO 방식으로 포인트 차감 처리
     */
    private void usePointsWithFifo(PointWallet pointWallet, int amount) {
        pointWallet.validateSufficientBalance(amount);

        List<Point> pointList = pointRepository.findUsablePointsByWalletId(pointWallet.getId());
        int originalAmount = amount;
        int remainingAmount = amount;

        for (Point point : pointList) {
            int availableBalance = point.getRemainingAmount();

            if (availableBalance >= remainingAmount) {
                point.decrease(remainingAmount);
                point.markUsed();
                break;
            } else {
                point.decrease(availableBalance);
                point.markUsed();
                remainingAmount -= availableBalance;
            }
        }

        pointWallet.decrease(originalAmount);
    }

    private PointWallet getPointWalletByUserId(Long userId) {
        return pointRepository.findWalletByUserId(userId)
            .orElseThrow(() -> new PointException(PointErrorCode.POINT_WALLET_NOT_FOUND));
    }

    private Point getPointByOrderId(String orderId) {
        return pointRepository.findBySourceOrderId(orderId)
            .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));
    }

    private LocalDateTime convertToStartDateTime(LocalDate startDate) {
        return startDate != null ? startDate.atStartOfDay() : null;
    }

    private LocalDateTime convertToEndDateTime(LocalDate endDate) {
        return endDate != null ? endDate.atTime(23, 59, 59) : null;
    }

    private PointReason convertToPointReason(String reason) {
        return StringUtils.hasText(reason) ? PointReason.of(reason) : null;
    }
}