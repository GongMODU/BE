package com.gong.modu.service.pipeline;

import com.gong.modu.util.ExternalNumberParser;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DisclosureFinancialStatementParser {

    // IPO 대상 회사의 매출액으로 신뢰 가능한 최소 임계값 (1억원).
    // 평탄화된 텍스트에서 항목 번호나 페이지 번호 같은 작은 숫자가 매출액으로 잘못 매칭되는 경우를 차단.
    // (실제 IPO 대상 회사는 보통 수십~수백억 단위. 0/null 은 매칭 자체가 없는 정상 케이스로 별도 처리.)
    private static final long REVENUE_SANITY_MIN = 100_000_000L;

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
            String year = years.get(i);
            Long revenue = scale(get(revenues, i), multiplier);
            Long operatingProfit = scale(get(operatingProfits, i), multiplier);
            Long netIncome = scale(get(netIncomes, i), multiplier);
            Long assets = scale(get(totalAssets, i), multiplier);
            Long liabilities = scale(get(totalLiabilities, i), multiplier);
            Long equity = scale(get(totalEquity, i), multiplier);

            if (!hasAnyValue(revenue, operatingProfit, netIncome, assets, liabilities, equity)) {
                continue;
            }

            // 매출액 sanity check: 음수 또는 임계값 미만이면 잘못 매칭된 신호로 판단해 행 전체 폐기.
            // (매출액과 다른 항목들이 같은 표에서 추출됐으므로 매출액이 의심스러우면 다른 값도 신뢰 불가)
            // 매출액이 null 인 경우는 표에서 매출액 라인을 못 찾은 정상 케이스라 허용.
            if (revenue != null && (revenue < 0 || revenue < REVENUE_SANITY_MIN)) {
                log.warn("[DisclosureFinancialStatementParser] 매출액 sanity 실패 → 해당 연도 폐기: year={}, revenue={}",
                        year, revenue);
                continue;
            }

            highlights.add(ParsedFinancialHighlight.builder()
                    .businessYear(year)
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

    // 사업연도 후보 추출 시 허용 범위
    // - 상한: 작년 (사업보고서는 결산 후 다음 해 초에 제출되므로 가장 최신이 작년)
    // - 하한: 상한에서 N년 전 (너무 옛날 연도가 끌려오지 않도록)
    // 미래 연도(전환사채 만기·락업 해제·예측 등) 와 무관한 옛 연도(설립일 등) 를 모두 배제하기 위함
    private static final int FUTURE_YEAR_BUFFER = 0;
    private static final int PAST_YEAR_BUFFER = 2;

    private List<String> findRecentYears(String section, int recentYears) {
        int latestAllowed = LocalDate.now().minusYears(1).getYear() + FUTURE_YEAR_BUFFER;
        int earliestAllowed = latestAllowed - Math.max(1, recentYears) - PAST_YEAR_BUFFER;

        Set<String> years = new LinkedHashSet<>();
        Matcher matcher = YEAR_PATTERN.matcher(section);

        while (matcher.find()) {
            String yearStr = matcher.group(1);
            int year = Integer.parseInt(yearStr);
            if (year >= earliestAllowed && year <= latestAllowed) {
                years.add(yearStr);
            }
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
