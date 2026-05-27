package com.gong.modu.repository.ipo;

import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.IpoEventType;
import com.gong.modu.domain.enums.ipo.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 공모주 일정 및 이벤트 조회 Repository
public interface IpoEventRepository extends JpaRepository<IpoEvent, Long> {

    // 대표 공시 접수번호 기준 조회
    Optional<IpoEvent> findByMainReportRceptNo(String mainReportRceptNo);

    // 특정 상태의 공모 이벤트 조회
    List<IpoEvent> findByStatus(IpoEventStatus status);

    // 특정 시장 공모 이벤트 조회
    List<IpoEvent> findByMarketType(MarketType marketType);

    // 특정 이벤트 유형 조회
    List<IpoEvent> findByEventType(IpoEventType eventType);

    // 청약 예정 공모주 조회
    List<IpoEvent> findBySubscriptionStartDateAfter(LocalDate date);

    // 홈 화면 청약 일정 리스트 필터별 기간 조회 (수요예측/청약/락업해제/환불/상장/배정)
    // 청약 시작일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findBySubscriptionStartDateBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 수요예측 시작일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findByDemandForecastStartBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 보호예수 해제일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findByLockupExpiryDateBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 환불일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findByRefundDateBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 상장일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findByListingDateBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 배정일이 특정 기간에 속하는 공모 이벤트 조회
    List<IpoEvent> findByAllocationDateBetween(LocalDate startInclusive, LocalDate endInclusive);

    // 현재 청약 진행중 공모주 조회
    List<IpoEvent> findBySubscriptionStartDateLessThanEqualAndSubscriptionEndDateGreaterThanEqual(
            LocalDate startDate,
            LocalDate endDate
    );

    // 상장 완료 공모주 조회
    List<IpoEvent> findByListingDateBefore(LocalDate date);

    // 특정 기업의 공모 이벤트 목록 조회
    List<IpoEvent> findByCompanyId(Long companyId);

    // 상장일 기준 최신순 조회
    List<IpoEvent> findAllByOrderByListingDateDesc();

    // 회사명 키워드 부분 일치 검색 (대소문자 구분 없음)
    // 청약이력 4.2 현재 청약 중 이력 등록 시 IpoEvent 선택용
    List<IpoEvent> findTop20ByCompany_CorpNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);

    // 특정 상태의 IpoEvent ID + 회사명을 projection으로 조회 (LazyInit 방지용)
    // 트랜잭션 없는 컨트롤러나 스케줄러에서 lazy 로딩 없이 일괄 처리할 때 사용
    @Query("SELECT e.id, e.company.corpName FROM IpoEvent e WHERE e.status = :status")
    List<Object[]> findIdAndCompanyNameByStatus(@Param("status") IpoEventStatus status);

    // 재파싱 대상 IpoEvent의 (id, companyName) projection 조회
    // 조건: status가 LISTED가 아니거나, listingDate가 추정값이거나, listingDate가 NULL
    @Query("SELECT e.id, e.company.corpName FROM IpoEvent e " +
            "WHERE e.status != :listedStatus " +
            "   OR e.listingDateEstimated = true " +
            "   OR e.listingDate IS NULL")
    List<Object[]> findIdAndCompanyNameForActiveIpos(@Param("listedStatus") IpoEventStatus listedStatus);

    // 재무 하이라이트 동기화 대상 IPO 기업 조회
    // 조건: 아직 상장 완료가 아니거나, 상장일이 추정값/NULL 인 활성 IPO
    @Query("SELECT DISTINCT e.company.id FROM IpoEvent e " +
            "WHERE e.status != :listedStatus " +
            "   OR e.listingDateEstimated = true " +
            "   OR e.listingDate IS NULL")
    List<Long> findDistinctCompanyIdsForActiveIpos(@Param("listedStatus") IpoEventStatus listedStatus);

    // 홈/상세 화면 노출 범위에 들어오는 IPO 기업 조회
    // 기능명세서 3.1.1 기준: 최근 3개월 ~ 향후 4주 사이의 주요 일정이 있는 공모주
    @Query("SELECT DISTINCT e.company.id FROM IpoEvent e " +
            "WHERE e.demandForecastStart BETWEEN :from AND :to " +
            "   OR e.subscriptionStartDate BETWEEN :from AND :to " +
            "   OR e.lockupExpiryDate BETWEEN :from AND :to " +
            "   OR e.refundDate BETWEEN :from AND :to " +
            "   OR e.listingDate BETWEEN :from AND :to " +
            "   OR e.allocationDate BETWEEN :from AND :to")
    List<Long> findDistinctCompanyIdsForHomeExposure(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 모든 IpoEvent의 status를 새로 계산하기 위한 일정 정보만 추출
    // (id, subscriptionStartDate, subscriptionEndDate, listingDate, 현재 status)
    @Query("SELECT e.id, e.subscriptionStartDate, e.subscriptionEndDate, e.listingDate, e.status FROM IpoEvent e")
    List<Object[]> findAllScheduleSummaries();
}
