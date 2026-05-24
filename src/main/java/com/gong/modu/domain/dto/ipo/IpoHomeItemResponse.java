package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.SignalLevel;
import com.gong.modu.domain.enums.ipo.SignalUnavailableReason;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 홈 화면 청약 일정 리스트의 공모주 한 건을 표현하는 응답 DTO
// 기능명세서 3.1.1 청약 일정 리스트업 / 3.1.2 핵심 지표 신호등 참고
@Getter
@Builder
public class IpoHomeItemResponse {

    // 공모 이벤트 ID (상세 화면 이동에 사용)
    private Long ipoEventId;

    // 회사명
    private String companyName;

    // 공모 진행 상태 (UPCOMING, ONGOING, CLOSED, LISTED)
    private IpoEventStatus status;

    // 디데이 라벨 (예: D-5, 청약시작일, 청약중, 청약종료일, 종료)
    private String dDayLabel;

    // 청약 시작일
    private LocalDate subscriptionStartDate;

    // 청약 종료일
    private LocalDate subscriptionEndDate;

    // 상장일 (실제 또는 청약종료일 + 7일 추정값)
    private LocalDate listingDate;

    // listingDate가 청약종료일 기준 추정값인지 여부 (true = 추정, false/null = 확정)
    private Boolean listingDateEstimated;

    // 락업해제일 (AI 직접 추출 또는 listingDate + 락업기간 계산값)
    private LocalDate lockupExpiryDate;

    // lockupExpiryDate가 계산 추정값인지 여부 (true = 추정, false/null = AI 직접 추출 확정)
    private Boolean lockupExpiryDateEstimated;

    // 희망 공모가 하단 (청약 시작 전 표시용)
    private BigDecimal offerPriceMin;

    // 희망 공모가 상단 (청약 시작 전 표시용)
    private BigDecimal offerPriceMax;

    // 확정 공모가 (청약 시작 이후 표시용)
    private BigDecimal offerPrice;

    // 청약 가능 증권사 목록
    private List<String> brokerNames;

    // 핵심 지표 신호등 등급 (지표가 부족하면 null)
    private SignalLevel signalLevel;

    // 신호등 산출에 사용한 위험 점수 (지표가 부족하면 null)
    private BigDecimal riskScore;

    // signalLevel이 null일 때 사유 (SPAC / PRE_DEMAND_FORECAST / INCOMPLETE_DATA)
    // signalLevel이 있으면 null
    private SignalUnavailableReason signalUnavailableReason;

    // 로그인 사용자의 관심 공모주 등록 여부 (비로그인 시 false)
    private boolean favorited;
}
