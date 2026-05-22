package com.gong.modu.domain.dto.pipeline;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
// AI가 공시 원문 일부를 읽고 추출한 값을 담는 DTO
public class AiDisclosureParsingResult {

    // 수요예측 시작일
    private String demandForecastStart;

    // 수요예측 종료일
    private String demandForecastEnd;

    // 환불일
    private String refundDate;

    // 상장일
    private String listingDate;

    // 보호예수 또는 의무보유 해제일
    private String lockupExpiryDate;

    // 희망 공모가 하단
    private BigDecimal offerPriceMin;

    // 희망 공모가 상단
    private BigDecimal offerPriceMax;

    // 확정 공모가
    private BigDecimal offerPrice;

    // 공모주식수
    private Long shareCount;

    // 상장예정주식수 또는 상장 후 총 주식수
    private Long totalListedShares;

    // 기관투자자 수요예측 경쟁률
    private BigDecimal institutionalCompetitionRate;

    // 의무보유확약 비율
    private BigDecimal lockupRatio;

    // 보호예수 또는 매각제한 비율
    private BigDecimal protectiveCustodyRatio;

    // AI가 판단한 간단한 근거
    // DB 저장용이 아닌 테스트 응답에서 확인하기 위함
    private String reason;
}
