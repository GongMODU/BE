package com.gong.modu.domain.dto.ipo;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IpoDisclosureReportResponse {

    private String companySummary;
    private String financialSummary;
    private SummarySection investorProtectionSummary;
    private SummarySection investmentPointSummary;
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
