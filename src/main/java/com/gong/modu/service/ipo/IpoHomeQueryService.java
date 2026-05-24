package com.gong.modu.service.ipo;

import com.gong.modu.domain.dto.ipo.IpoEventSearchItemResponse;
import com.gong.modu.domain.dto.ipo.IpoHomeItemResponse;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.entity.ipo.IpoEventBroker;
import com.gong.modu.domain.entity.ipo.IpoOffering;
import com.gong.modu.domain.enums.ipo.IpoScheduleFilter;
import com.gong.modu.domain.enums.ipo.SignalLevel;
import com.gong.modu.domain.enums.ipo.SignalUnavailableReason;
import com.gong.modu.repository.ipo.IpoEventBrokerRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.service.user.InterestIpoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// 홈 화면 청약 일정 리스트업을 담당하는 조회 서비스
// 기능명세서 3.1.1 청약 일정 리스트업 참고
@Service
@RequiredArgsConstructor
public class IpoHomeQueryService {

    // 일정 기준일로부터 노출할 과거/미래 범위
    private static final int PAST_MONTHS = 3;  // 최근 3개월 전까지
    private static final int FUTURE_WEEKS = 4; // 향후 4주(다음 달)까지

    private final IpoEventRepository ipoEventRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;
    private final IpoSignalCalculator ipoSignalCalculator;
    private final InterestIpoService interestIpoService;

    // 홈 화면용 청약 일정 리스트를 필터 기준으로 조회하는 메서드
    // 필터에 해당하는 일정일이 최근 3개월~향후 4주 범위에 드는 공모주를 일정일 순으로 반환
    // userId가 있으면 각 항목의 favorited 필드를 채워 반환
    @Transactional(readOnly = true)
    public List<IpoHomeItemResponse> getHomeSchedule(IpoScheduleFilter filter, Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusMonths(PAST_MONTHS);
        LocalDate to = today.plusWeeks(FUTURE_WEEKS);

        Set<Long> favoritedIds = interestIpoService.getInterestedIpoEventIds(userId);

        return findEventsByFilter(filter, from, to).stream()
                .filter(event -> filterDate(event, filter) != null)
                .sorted(Comparator.comparing((IpoEvent event) -> filterDate(event, filter)))
                .map(event -> toHomeItem(event, today, filter, favoritedIds.contains(event.getId())))
                .toList();
    }

    // 회사명 키워드로 IpoEvent를 검색하는 메서드 (청약이력 4.2 종목 선택용)
    @Transactional(readOnly = true)
    public List<IpoEventSearchItemResponse> searchIpoEvents(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        return ipoEventRepository
                .findTop20ByCompany_CorpNameContainingIgnoreCaseOrderByCreatedAtDesc(keyword.trim())
                .stream()
                .map(this::toSearchItem)
                .toList();
    }

    private IpoEventSearchItemResponse toSearchItem(IpoEvent event) {
        return IpoEventSearchItemResponse.builder()
                .ipoEventId(event.getId())
                .companyName(event.getCompany().getCorpName())
                .marketType(event.getMarketType())
                .status(event.resolveStatus(LocalDate.now()))
                .subscriptionStartDate(event.getSubscriptionStartDate())
                .subscriptionEndDate(event.getSubscriptionEndDate())
                .listingDate(event.getListingDate())
                .build();
    }

    // 필터 종류에 따라 해당 일정일 기준으로 공모 이벤트를 조회하는 메서드
    private List<IpoEvent> findEventsByFilter(IpoScheduleFilter filter, LocalDate from, LocalDate to) {
        return switch (filter) {
            case DEMAND_FORECAST -> ipoEventRepository.findByDemandForecastStartBetween(from, to);
            case SUBSCRIPTION -> ipoEventRepository.findBySubscriptionStartDateBetween(from, to);
            case LOCKUP_EXPIRY -> ipoEventRepository.findByLockupExpiryDateBetween(from, to);
            case REFUND -> ipoEventRepository.findByRefundDateBetween(from, to);
            case LISTING -> ipoEventRepository.findByListingDateBetween(from, to);
            case ALLOCATION -> ipoEventRepository.findByAllocationDateBetween(from, to);
        };
    }

    // 필터 종류에 해당하는 공모 이벤트의 일정일을 반환하는 메서드
    private LocalDate filterDate(IpoEvent event, IpoScheduleFilter filter) {
        return switch (filter) {
            case DEMAND_FORECAST -> event.getDemandForecastStart();
            case SUBSCRIPTION -> event.getSubscriptionStartDate();
            case LOCKUP_EXPIRY -> event.getLockupExpiryDate();
            case REFUND -> event.getRefundDate();
            case LISTING -> event.getListingDate();
            case ALLOCATION -> event.getAllocationDate();
        };
    }

    // 공모 이벤트 한 건을 홈 리스트 응답 DTO로 변환하는 메서드
    private IpoHomeItemResponse toHomeItem(IpoEvent event, LocalDate today, IpoScheduleFilter filter, boolean favorited) {
        IpoOffering offering = event.getOffering();
        IpoSignalCalculator.SignalResult signal = ipoSignalCalculator.calculate(event.getMetric());

        // 해당 공모에 참여한 청약 가능 증권사명 목록
        List<String> brokerNames = ipoEventBrokerRepository.findByIpoEventId(event.getId()).stream()
                .map(IpoEventBroker::getBrokerName)
                .distinct()
                .toList();

        return IpoHomeItemResponse.builder()
                .ipoEventId(event.getId())
                .companyName(event.getCompany().getCorpName())
                .status(event.resolveStatus(today))
                .dDayLabel(buildDDayLabel(event, today, filter))
                .subscriptionStartDate(event.getSubscriptionStartDate())
                .subscriptionEndDate(event.getSubscriptionEndDate())
                .listingDate(event.getListingDate())
                .listingDateEstimated(event.getListingDateEstimated())
                .lockupExpiryDate(event.getLockupExpiryDate())
                .lockupExpiryDateEstimated(event.getLockupExpiryDateEstimated())
                .offerPriceMin(offering == null ? null : offering.getOfferPriceMin())
                .offerPriceMax(offering == null ? null : offering.getOfferPriceMax())
                .offerPrice(offering == null ? null : offering.getOfferPrice())
                .brokerNames(brokerNames)
                .signalLevel(signal.signalLevel())
                .riskScore(signal.riskScore())
                .signalUnavailableReason(resolveSignalUnavailableReason(event, signal.signalLevel(), today))
                .favorited(favorited)
                .build();
    }

    // signalLevel이 null인 사유를 결정하는 메서드
    // - 스팩 종목: SPAC
    // - 수요예측 종료일이 미래거나 없음: PRE_DEMAND_FORECAST
    // - 그 외(수요예측 후인데 지표 부족): INCOMPLETE_DATA
    static SignalUnavailableReason resolveSignalUnavailableReason(IpoEvent event, SignalLevel signalLevel, LocalDate today) {
        if (signalLevel != null) {
            return null;
        }

        String corpName = event.getCompany().getCorpName();
        if (corpName != null && (corpName.contains("기업인수목적") || corpName.contains("스팩"))) {
            return SignalUnavailableReason.SPAC;
        }

        LocalDate demandEnd = event.getDemandForecastEnd();
        if (demandEnd == null || !today.isAfter(demandEnd)) {
            return SignalUnavailableReason.PRE_DEMAND_FORECAST;
        }

        return SignalUnavailableReason.INCOMPLETE_DATA;
    }

    // 필터 기준으로 디데이 라벨을 만드는 메서드
    // 청약 필터는 시작/종료일을 함께 사용한 세분화된 라벨, 그 외 필터는 단일 일정일 기준
    private String buildDDayLabel(IpoEvent event, LocalDate today, IpoScheduleFilter filter) {
        if (filter == IpoScheduleFilter.SUBSCRIPTION) {
            return buildSubscriptionDDayLabel(event, today);
        }

        LocalDate target = filterDate(event, filter);

        if (target == null) {
            return null;
        }

        if (today.isBefore(target)) {
            return "D-" + ChronoUnit.DAYS.between(today, target);
        }

        if (today.isEqual(target)) {
            return "D-DAY";
        }

        return "종료";
    }

    // 청약 시작/종료일과 오늘 날짜를 비교해 청약 디데이 라벨을 만드는 메서드
    private String buildSubscriptionDDayLabel(IpoEvent event, LocalDate today) {
        LocalDate start = event.getSubscriptionStartDate();
        LocalDate end = event.getSubscriptionEndDate();

        // 청약 시작일이 없으면 디데이를 계산할 수 없음
        if (start == null) {
            return null;
        }

        // 청약 시작 전: 남은 일수를 D-N으로 표시
        if (today.isBefore(start)) {
            return "D-" + ChronoUnit.DAYS.between(today, start);
        }

        // 청약 시작일 당일
        if (today.isEqual(start)) {
            return "청약시작일";
        }

        // 청약 종료일 당일
        if (end != null && today.isEqual(end)) {
            return "청약종료일";
        }

        // 청약 종료일 경과: 종료된 청약도 홈에 남겨 <종료>로 표시
        if (end != null && today.isAfter(end)) {
            return "종료";
        }

        // 청약 시작과 종료 사이
        return "청약중";
    }
}
