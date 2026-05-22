package com.gong.modu.service.pipeline;

import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.domain.dto.pipeline.IpoDisclosureParsingResult;
import com.gong.modu.util.ExternalDateParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
// AI 파싱 결과를 검증·변환해 최종 IpoDisclosureParsingResult로 만드는 클래스
// 정규식 파싱은 오탐이 많아 값 추출에서 제외되었고, AI 결과만 신뢰한다
public class DisclosureParsingResultMerger {

    // AI 파싱 결과를 검증·타입변환하여 최종 IpoDisclosureParsingResult를 생성하는 메서드
    public IpoDisclosureParsingResult toResult(AiDisclosureParsingResult aiResult) {
        // AI 결과가 없으면 빈 결과 객체 반환
        if (aiResult == null) {
            return IpoDisclosureParsingResult.builder().build();
        }

        return IpoDisclosureParsingResult.builder()
                .demandForecastStart(parseAiDate(aiResult.getDemandForecastStart()))
                .demandForecastEnd(parseAiDate(aiResult.getDemandForecastEnd()))
                .refundDate(parseAiDate(aiResult.getRefundDate()))
                .listingDate(parseAiDate(aiResult.getListingDate()))
                .lockupExpiryDate(parseAiDate(aiResult.getLockupExpiryDate()))
                .offerPriceMin(validMoney(aiResult.getOfferPriceMin()))
                .offerPriceMax(validMoney(aiResult.getOfferPriceMax()))
                .offerPrice(validMoney(aiResult.getOfferPrice()))
                .shareCount(validPositiveLong(aiResult.getShareCount()))
                .totalListedShares(validPositiveLong(aiResult.getTotalListedShares()))
                .institutionalCompetitionRate(validMoney(aiResult.getInstitutionalCompetitionRate()))
                .lockupRatio(validRatio(aiResult.getLockupRatio()))
                .protectiveCustodyRatio(validRatio(aiResult.getProtectiveCustodyRatio()))
                .build();
    }

    // AI 날짜 문자열을 LocalDate로 변환하는 메서드
    private LocalDate parseAiDate(String value) {
        if (value == null || value.isBlank())
            return null;

        // AI가 "null", "N/A", "미기재" 같은 문자열을 줄 수 있으므로 걸러냄
        String normalized = value.trim();

        if ("null".equalsIgnoreCase(normalized)
                || "n/a".equalsIgnoreCase(normalized)
                || "미기재".equals(normalized)
                || "확인불가".equals(normalized)) {
            return null;
        }

        return ExternalDateParser.parseFlexibleDate(normalized);
    }

    // 0 이상이어야 하는 숫자를 검증하는 메서드
    private BigDecimal validMoney(BigDecimal value) {
        if (value == null)
            return null;

        if (value.compareTo(BigDecimal.ZERO) < 0)
            return null;

        return value;
    }

    // 비율 값이 0~1 범위인지 검증하는 메서드
    private BigDecimal validRatio(BigDecimal value) {
        if (value == null)
            return null;

        if (value.compareTo(BigDecimal.ZERO) < 0)
            return null;

        if (value.compareTo(BigDecimal.ONE) > 0)
            return null;

        return value;
    }

    // Long 값이 0보다 큰지 검증하는 메서드
    private Long validPositiveLong(Long value) {
        if (value == null)
            return null;

        if (value <= 0)
            return null;

        return value;
    }
}
