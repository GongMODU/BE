package com.gong.modu.domain.dto.ipo;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class IpoSummaryResult {

    private List<String> companySummary;
    private List<String> financialSummary;
    private SummarySection investorProtectionSummary;
    private SummarySection mergerInfoSummary;
    private List<RiskItem> riskSummary;

    @Getter
    @NoArgsConstructor
    public static class SummarySection {
        private String highlight;
        private List<SummaryItem> items;
    }

    @Getter
    @NoArgsConstructor
    public static class SummaryItem {
        private String title;
        private String content;
    }

    @Getter
    @NoArgsConstructor
    public static class RiskItem {
        private String title;
        private String content;
    }
}
