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

import java.util.List;

// 청약 이력 (4.1 과거 투자 이력 + 4.2 현재 청약 중 이력) 관리 서비스
@Service
@RequiredArgsConstructor
public class SubscriptionHistoryService {

    private final UserRepository userRepository;
    private final IpoEventRepository ipoEventRepository;
    private final UserSubscriptionHistoryRepository historyRepository;

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
