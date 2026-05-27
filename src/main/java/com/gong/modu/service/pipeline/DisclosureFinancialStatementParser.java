package com.gong.modu.service.pipeline;

import com.gong.modu.util.ExternalNumberParser;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DisclosureFinancialStatementParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\(?-?\\d{1,3}(?:,\\d{3})+\\)?|\\(?-?\\d+\\)?");
    private static final int SECTION_WINDOW_BEFORE = 2_000;
    private static final int SECTION_WINDOW_AFTER = 40_000;

    public List<ParsedFinancialHighlight> parse(String originalText, int recentYears) {
        if (originalText == null || originalText.isBlank()) {
            return List.of();
        }

        String section = extractFinancialSection(originalText);
        List<String> years = findRecentYears(section, recentYears);

        if (years.isEmpty()) {
            return List.of();
        }

        long multiplier = detectAmountMultiplier(section);

        List<Long> revenues = findAmounts(section, "매출액", recentYears);
        List<Long> operatingProfits = findAmounts(section, "영업이익", recentYears);
        List<Long> netIncomes = findAmounts(section, "당기순이익", recentYears);
        List<Long> totalAssets = findAmounts(section, "자산총계", recentYears);
        List<Long> totalLiabilities = findAmounts(section, "부채총계", recentYears);
        List<Long> totalEquity = findAmounts(section, "자본총계", recentYears);

        List<ParsedFinancialHighlight> highlights = new ArrayList<>();

        for (int i = 0; i < years.size(); i++) {
            Long revenue = scale(get(revenues, i), multiplier);
            Long operatingProfit = scale(get(operatingProfits, i), multiplier);
            Long netIncome = scale(get(netIncomes, i), multiplier);
            Long assets = scale(get(totalAssets, i), multiplier);
            Long liabilities = scale(get(totalLiabilities, i), multiplier);
            Long equity = scale(get(totalEquity, i), multiplier);

            if (!hasAnyValue(revenue, operatingProfit, netIncome, assets, liabilities, equity)) {
                continue;
            }

            highlights.add(ParsedFinancialHighlight.builder()
                    .businessYear(years.get(i))
                    .revenue(revenue)
                    .operatingProfit(operatingProfit)
                    .netIncome(netIncome)
                    .totalAssets(assets)
                    .totalLiabilities(liabilities)
                    .totalEquity(equity)
                    .currency("KRW")
                    .build());
        }

        return highlights;
    }

    // 재무 섹션 텍스트에서 금액 단위를 감지해 원 단위 배수를 반환
    private long detectAmountMultiplier(String section) {
        if (section.contains("백만원")) return 1_000_000L;
        if (section.contains("천원"))   return 1_000L;
        return 1L;
    }

    private Long scale(Long value, long multiplier) {
        return value != null ? value * multiplier : null;
    }

    private String extractFinancialSection(String originalText) {
        String normalized = originalText.replace('\u00A0', ' ');
        List<String> anchors = List.of(
                "요약재무정보",
                "요약 재무정보",
                "재무제표",
                "재무상태표",
                "손익계산서",
                "포괄손익계산서",
                "재무에 관한 사항"
        );

        int startIndex = -1;
        for (String anchor : anchors) {
            int index = normalized.indexOf(anchor);
            if (index >= 0 && (startIndex < 0 || index < startIndex)) {
                startIndex = index;
            }
        }

        if (startIndex < 0) {
            return normalized.length() > SECTION_WINDOW_AFTER
                    ? normalized.substring(0, SECTION_WINDOW_AFTER)
                    : normalized;
        }

        int start = Math.max(0, startIndex - SECTION_WINDOW_BEFORE);
        int end = Math.min(normalized.length(), startIndex + SECTION_WINDOW_AFTER);
        return normalized.substring(start, end);
    }

    private List<String> findRecentYears(String section, int recentYears) {
        Set<String> years = new LinkedHashSet<>();
        Matcher matcher = YEAR_PATTERN.matcher(section);

        while (matcher.find()) {
            years.add(matcher.group(1));
        }

        return years.stream()
                .sorted((a, b) -> b.compareTo(a))
                .limit(Math.max(1, recentYears))
                .toList();
    }

    private List<Long> findAmounts(String section, String accountName, int recentYears) {
        List<String> lines = section.lines()
                .filter(line -> line.contains(accountName))
                .filter(line -> !line.contains(accountName + "률"))
                .toList();

        for (String line : lines) {
            List<Long> amounts = extractAmounts(line);

            if (!amounts.isEmpty()) {
                return amounts.stream()
                        .limit(Math.max(1, recentYears))
                        .toList();
            }
        }

        int accountIndex = section.indexOf(accountName);
        if (accountIndex >= 0) {
            int end = Math.min(section.length(), accountIndex + 500);
            List<Long> amounts = extractAmounts(section.substring(accountIndex, end));

            if (!amounts.isEmpty()) {
                return amounts.stream()
                        .limit(Math.max(1, recentYears))
                        .toList();
            }
        }

        return List.of();
    }

    private List<Long> extractAmounts(String line) {
        List<Long> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(line);

        while (matcher.find()) {
            String token = matcher.group();

            if (isYearToken(token)) {
                continue;
            }

            Long amount = ExternalNumberParser.toLong(normalizeNegative(token));
            if (amount != null) {
                amounts.add(amount);
            }
        }

        return amounts;
    }

    private boolean isYearToken(String token) {
        String normalized = token.replace(",", "").replace("(", "").replace(")", "");
        return YEAR_PATTERN.matcher(normalized).matches();
    }

    private String normalizeNegative(String token) {
        String trimmed = token.trim();

        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return "-" + trimmed.substring(1, trimmed.length() - 1);
        }

        return trimmed;
    }

    private Long get(List<Long> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private boolean hasAnyValue(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return true;
            }
        }

        return false;
    }

    @Getter
    @Builder
    public static class ParsedFinancialHighlight {
        private final String businessYear;
        private final Long revenue;
        private final Long operatingProfit;
        private final Long netIncome;
        private final Long totalAssets;
        private final Long totalLiabilities;
        private final Long totalEquity;
        private final String currency;
    }
}
