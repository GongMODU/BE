package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.SignalLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 공모주 상세 화면 응답 DTO
// 기능명세서 3.1.3 공모주 상세 정보 / 와이어프레임의 청약·예측·기업 탭 구조 기반
// 공시 리포트 요약은 별도 API(IpoDisclosureReportResponse), 재무 차트는 IpoFinancialResponse를 사용
@Getter
@Builder
public class IpoDetailResponse {

    // 공모 이벤트 ID
    private Long ipoEventId;

    // 회사명
    private String companyName;

    // 핵심 지표 신호등 등급 (지표 부족 시 null)
    private SignalLevel signalLevel;

    // 신호등 산출 위험 점수 (지표 부족 시 null)
    private BigDecimal riskScore;

    // 청약 탭 정보
    private SubscriptionTab subscription;

    // 예측 탭 정보
    private ForecastTab forecast;

    // 기업 탭 정보
    private CompanyTab company;

    @Schema(description = "로그인 사용자의 관심 공모주(찜) 등록 여부. 비로그인 시 항상 false", example = "false")
    private boolean favorited;

    // 청약 탭: 청약·배정 관련 정보
    @Getter
    @Builder
    public static class SubscriptionTab {

        // 청약 시작일
        private LocalDate subscriptionStartDate;

        // 청약 종료일
        private LocalDate subscriptionEndDate;

        // 상장일
        private LocalDate listingDate;

        // 환불일
        private LocalDate refundDate;

        // 일반청약 경쟁률
        private BigDecimal generalSubscriptionRate;

        // 비례배정 경쟁률
        private BigDecimal proportionalCompetitionRate;

        // 균등배정 주식 수
        private Long equalAllocationShares;

        // 일반배정 주식 수
        private Long generalAllocationShares;
    }

    // 예측 탭: 수요예측·공모가 관련 정보
    @Getter
    @Builder
    public static class ForecastTab {

        // 확정 공모가
        private BigDecimal offerPrice;

        // 수요예측 시작일
        private LocalDate demandForecastStart;

        // 수요예측 종료일
        private LocalDate demandForecastEnd;

        // 희망 공모가 하단
        private BigDecimal offerPriceMin;

        // 희망 공모가 상단
        private BigDecimal offerPriceMax;

        // 의무보유확약 비율
        private BigDecimal lockupRatio;

        // 기관투자자 경쟁률
        private BigDecimal institutionalCompetitionRate;
    }

    // 기업 탭: 재무·공모규모·증권사 관련 정보
    @Getter
    @Builder
    public static class CompanyTab {

        // 매출액 (최근 사업연도)
        private Long revenue;

        // 당기순이익 (최근 사업연도)
        private Long netIncome;

        // 공모주식수
        private Long shareCount;

        // 상장 후 총 주식수
        private Long totalListedShares;

        // 보호예수 비율
        private BigDecimal protectiveCustodyRatio;

        // 청약 가능 증권사 목록
        private List<String> brokerNames;
    }
}
