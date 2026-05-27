package com.gong.modu.domain.dto.ipo;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IpoDisclosureReportResponse {

    // 회사 기본 정보 (텍스트 파싱 없이 DB에서 직접 제공)
    private String companyName;
    private Boolean isSpac;
    private LocalDate establishedAt;
    private LocalDate listingDate;

    // AI 요약 필드
    private String companySummary;
    private String financialSummary;

    // 투자자 보호 안전장치 요약 (SPAC/일반 공모주 모두 항상 존재)
    private SummarySection investorProtectionSummary;

    // 합병 목표 및 유효 기한 요약 (SPAC 전용, 일반 공모주는 null)
    private SummarySection mergerInfoSummary;

    private List<RiskItem> riskSummary;
    private String summaryVersion;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummarySection {
        private String highlight;
        private List<SummaryItem> items;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryItem {
        private String title;
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskItem {
        private String title;
        private String content;
    }
}
