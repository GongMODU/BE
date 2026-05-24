package com.gong.modu.repository.user;

import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.entity.user.UserSubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

// 사용자 청약 이력 조회 Repository
public interface UserSubscriptionHistoryRepository extends JpaRepository<UserSubscriptionHistory, Long> {
    // 특정 사용자의 전체 청약 이력 조회
    List<UserSubscriptionHistory> findByUser(User user);

    // 특정 사용자의 청약 이력을 최신순으로 조회
    List<UserSubscriptionHistory> findByUserOrderByCreatedAtDesc(User user);

    // 특정 사용자의 특정 상태 청약 이력 조회
    List<UserSubscriptionHistory> findByUserAndRecordStatus(
            User user,
            SubscriptionRecordStatus recordStatus
    );

    // 특정 사용자의 특정 상태 + 매도일이 from 이상인 청약 이력 조회
    // 평균 수익률 계산(3.3 기능)에서 최근 N개월 범위로 좁힐 때 사용
    List<UserSubscriptionHistory> findByUserAndRecordStatusAndSellDateGreaterThanEqual(
            User user,
            SubscriptionRecordStatus recordStatus,
            LocalDate from
    );

    // 특정 공모 이벤트에 연결된 사용자 청약 이력 조회
    List<UserSubscriptionHistory> findByIpoEventId(Long ipoEventId);

    // 특정 사용자가 특정 공모 이벤트에 대해 남긴 청약 이력 조회
    List<UserSubscriptionHistory> findByUserAndIpoEventId(User user, Long ipoEventId);
}
