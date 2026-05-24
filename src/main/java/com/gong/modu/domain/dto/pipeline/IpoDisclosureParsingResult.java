package com.gong.modu.domain.dto.pipeline;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
// 공시 원문 텍스트에서 추출한 IPO 핵심 값들을 임시로 담는 DTO
public class IpoDisclosureParsingResult {

    // 수요예측 시작일
    private LocalDate demandForecastStart;

    // 수요예측 종료일
    private LocalDate demandForecastEnd;

    // 청약 시작일
    private LocalDate subscriptionStart;

    // 청약 종료일
    private LocalDate subscriptionEnd;

    // 환불일
    private LocalDate refundDate;

    // 상장일
    private LocalDate listingDate;

    // 락업해제일
    private LocalDate lockupExpiryDate;

    // 대표 락업 기간(개월). 해제일이 null이고 listingDate가 있을 때 계산에 사용
    private Integer lockupPeriodMonths;

    // 희망 공모가 하단
    private BigDecimal offerPriceMin;

    // 희망 공모가 상단
    private BigDecimal offerPriceMax;

    // 확정 공모가
    private BigDecimal offerPrice;

    // 공모주식수
    private Long shareCount;

    // 상장주식수
    private Long totalListedShares;

    // 기관경쟁률
    private BigDecimal institutionalCompetitionRate;

    // 의무보유확약 비율
    private BigDecimal lockupRatio;

    // 보호예수 비율
    private BigDecimal protectiveCustodyRatio;

    // 주관사·인수증권사 목록
    private List<String> brokerNames;
}
