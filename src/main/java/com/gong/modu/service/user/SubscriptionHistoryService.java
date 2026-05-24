package com.gong.modu.service.user;

import com.gong.modu.domain.dto.user.*;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.entity.user.UserSubscriptionHistory;
import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.repository.user.UserRepository;
import com.gong.modu.repository.user.UserSubscriptionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 청약 이력 (4.1 과거 투자 이력 + 4.2 현재 청약 중 이력) 관리 서비스
@Service
@RequiredArgsConstructor
public class SubscriptionHistoryService {

    // 평균 수익률 그래프 기간 파라미터의 허용 범위 (1~24개월)
    private static final int MIN_MONTHS = 1;
    private static final int MAX_MONTHS = 24;

    private final UserRepository userRepository;
    private final IpoEventRepository ipoEventRepository;
    private final UserSubscriptionHistoryRepository historyRepository;
    private final ReturnRateCalculator returnRateCalculator;

    // 4.1 과거 투자 이력 생성 (DB 종목 조회 없이 사용자 직접 입력, status = COMPLETED)
    @Transactional
    public Long createCompleted(Long userId, CompletedHistoryCreateRequest request) {
        User user = getUser(userId);

        UserSubscriptionHistory history = UserSubscriptionHistory.builder()
                .user(user)
                .recordStatus(SubscriptionRecordStatus.COMPLETED)
                .inputStockName(request.getInputStockName())
                .inputCompanyName(request.getInputCompanyName())
                .securityCompany(request.getSecurityCompany())
                .subscribedQuantity(request.getSubscribedQuantity())
                .allocatedQuantity(request.getAllocatedQuantity())
                .offerPrice(request.getOfferPrice())
                .sellPrice(request.getSellPrice())
                .fee(request.getFee())
                .tax(request.getTax())
                .sellDate(request.getSellDate())
                .memo(request.getMemo())
                .build();

        return historyRepository.save(history).getId();
    }

    // 4.2 현재 청약 중 이력 생성 (IpoEvent와 연결 필수, status = ONGOING)
    @Transactional
    public Long createOngoing(Long userId, OngoingHistoryCreateRequest request) {
        User user = getUser(userId);

        IpoEvent ipoEvent = ipoEventRepository.findById(request.getIpoEventId())
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        UserSubscriptionHistory history = UserSubscriptionHistory.builder()
                .user(user)
                .ipoEvent(ipoEvent)
                .recordStatus(SubscriptionRecordStatus.ONGOING)
                .securityCompany(request.getSecurityCompany())
                .subscribedQuantity(request.getSubscribedQuantity())
                .offerPrice(request.getOfferPrice())
                .subscriptionAmount(request.getSubscriptionAmount())
                .memo(request.getMemo())
                .build();

        return historyRepository.save(history).getId();
    }

    // 내 청약 이력 전체 또는 상태별 조회 (최신순)
    @Transactional(readOnly = true)
    public List<SubscriptionHistoryResponse> getMyHistories(Long userId, SubscriptionRecordStatus status) {
        User user = getUser(userId);

        List<UserSubscriptionHistory> histories = (status == null)
                ? historyRepository.findByUserOrderByCreatedAtDesc(user)
                : historyRepository.findByUserAndRecordStatus(user, status);

        return histories.stream()
                .map(SubscriptionHistoryResponse::from)
                .toList();
    }

    // 청약이력 기반 평균 수익률 요약 조회 (기능명세서 3.3)
    // 최근 months 개월의 COMPLETED 이력을 매도일 기준 월별 그룹핑 → 평균 수익률 + 이번달/저번달 트렌드 반환
    @Transactional(readOnly = true)
    public ReturnRateSummaryResponse getReturnRateSummary(Long userId, int months) {
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_HISTORY_INVALID_INPUT);
        }

        User user = getUser(userId);

        LocalDate today = LocalDate.now();
        // months - 1 인 이유: 이번달 포함해서 총 months 개월이 되도록
        // 예: months=6, today=2026-05-24 → from = 2025-12-01 (12월~5월 = 6개월)
        YearMonth fromYearMonth = YearMonth.from(today).minusMonths(months - 1L);
        LocalDate fromDate = fromYearMonth.atDay(1);

        List<UserSubscriptionHistory> histories = historyRepository
                .findByUserAndRecordStatusAndSellDateGreaterThanEqual(
                        user,
                        SubscriptionRecordStatus.COMPLETED,
                        fromDate
                );

        return returnRateCalculator.summarize(histories, months, today);
    }

    // 단건 조회
    @Transactional(readOnly = true)
    public SubscriptionHistoryResponse getHistory(Long userId, Long historyId) {
        UserSubscriptionHistory history = findOwnedHistory(userId, historyId);
        return SubscriptionHistoryResponse.from(history);
    }

    // 청약 이력 수정 (null인 필드는 기존 값 유지)
    @Transactional
    public void updateHistory(Long userId, Long historyId, SubscriptionHistoryUpdateRequest request) {
        UserSubscriptionHistory history = findOwnedHistory(userId, historyId);

        history.updateUserEditableFields(
                request.getInputStockName(),
                request.getInputCompanyName(),
                request.getSecurityCompany(),
                request.getSubscribedQuantity(),
                request.getAllocatedQuantity(),
                request.getOfferPrice(),
                request.getSubscriptionAmount(),
                request.getSellPrice(),
                request.getFee(),
                request.getTax(),
                request.getSellDate(),
                request.getMemo()
        );
    }

    // ONGOING 이력에 매도 정보를 입력해 COMPLETED 로 전환
    @Transactional
    public void completeHistory(Long userId, Long historyId, CompleteHistoryRequest request) {
        UserSubscriptionHistory history = findOwnedHistory(userId, historyId);

        history.completeRecord(
                request.getSellPrice(),
                request.getFee(),
                request.getTax(),
                request.getSellDate()
        );
    }

    // 삭제
    @Transactional
    public void deleteHistory(Long userId, Long historyId) {
        UserSubscriptionHistory history = findOwnedHistory(userId, historyId);
        historyRepository.delete(history);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private UserSubscriptionHistory findOwnedHistory(Long userId, Long historyId) {
        UserSubscriptionHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND));

        if (!history.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_HISTORY_FORBIDDEN);
        }

        return history;
    }
}
