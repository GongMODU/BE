package com.gong.modu.service.pipeline;

import com.gong.modu.client.DartApiClient;
import com.gong.modu.domain.dto.dart.DartFinancialStatementResponse;
import com.gong.modu.domain.entity.ipo.Company;
import com.gong.modu.domain.entity.ipo.CompanyFinancialHighlight;
import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import com.gong.modu.domain.enums.ipo.FinancialStatementType;
import com.gong.modu.domain.enums.ipo.ReportCode;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.CompanyFinancialHighlightRepository;
import com.gong.modu.repository.ipo.CompanyRepository;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.util.ExternalNumberParser;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
// DART 재무제표 API 응답을 company_financial_highlights 테이블에 저장/갱신하는 서비스
public class DartFinancialSyncService {
    private static final Pattern BUSINESS_YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final int HOME_EXPOSURE_PAST_MONTHS = 3;
    private static final int HOME_EXPOSURE_FUTURE_WEEKS = 4;

    private final DartApiClient dartApiClient;
    private final CompanyRepository companyRepository;
    private final IpoEventRepository ipoEventRepository;
    private final IpoDisclosureReportRepository disclosureReportRepository;
    private final CompanyFinancialHighlightRepository financialHighlightRepository;
    private final DisclosureFinancialStatementParser disclosureFinancialStatementParser;

    @Transactional
    // 특정 기업의 특정 연도/보고서/재무제표 구분에 해당하는 재무 하이라이트를 동기화하는 메서드
    public CompanyFinancialHighlight syncFinancialHighlight(
            Long companyId,
            String businessYear,
            ReportCode reportCode,
            FinancialStatementType financialStatementType
    ) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // DART 단일회사 전체 재무제표 API 호출
        DartFinancialStatementResponse response = dartApiClient.getFinancialStatements(
                company.getCorpCode(),
                businessYear,
                reportCode.getCode(),
                financialStatementType.name()
        );

        // DART 재무제표 응답의 실제 계정과목 목록
        List<DartFinancialStatementResponse.Item> items = response.getList();

        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.FINANCIAL_DATA_NOT_FOUND);
        }

        Long revenue = findAmount(items, "매출액");
        Long operatingProfit = findAmount(items, "영업이익");
        Long netIncome = findAmount(items, "당기순이익");
        Long totalAssets = findAmount(items, "자산총계");
        Long totalLiabilities = findAmount(items, "부채총계");
        Long totalEquity = findAmount(items, "자본총계");
        String currency = findCurrency(items);

        return saveFinancialHighlight(
                company,
                businessYear,
                reportCode,
                financialStatementType,
                revenue,
                operatingProfit,
                netIncome,
                totalAssets,
                totalLiabilities,
                totalEquity,
                currency
        );

    }

    private CompanyFinancialHighlight saveFinancialHighlight(
            Company company,
            String businessYear,
            ReportCode reportCode,
            FinancialStatementType financialStatementType,
            Long revenue,
            Long operatingProfit,
            Long netIncome,
            Long totalAssets,
            Long totalLiabilities,
            Long totalEquity,
            String currency
    ) {
        return financialHighlightRepository
                .findByCompanyIdAndBsnsYearAndReportCodeAndFinancialStatementType(
                        company.getId(),
                        businessYear,
                        reportCode,
                        financialStatementType
                ) // 같은 구분의 데이터가 이미 있는지 검사
                .map(existing -> {
                    existing.updateFinancials( // 최신 응답값으로 갱신
                            revenue,
                            operatingProfit,
                            netIncome,
                            totalAssets,
                            totalLiabilities,
                            totalEquity,
                            currency
                    );

                    return existing; // 갱신된 기존 엔티티 반환

                }).orElseGet(() -> financialHighlightRepository.save(
                        CompanyFinancialHighlight.builder()
                                .company(company)
                                .bsnsYear(businessYear)
                                .reportCode(reportCode)
                                .financialStatementType(financialStatementType)
                                .revenue(revenue)
                                .operatingProfit(operatingProfit)
                                .netIncome(netIncome)
                                .totalAssets(totalAssets)
                                .totalLiabilities(totalLiabilities)
                                .totalEquity(totalEquity)
                                .currency(currency)
                                .build()
                ));
    }

    @Transactional
    public FinancialHighlightSyncResult syncCompanyAnnualFinancialHighlights(Long companyId, int recentYears) {
        return syncAnnualFinancialHighlights(List.of(companyId), recentYears);
    }

    @Transactional
    public FinancialHighlightSyncResult syncActiveIpoAnnualFinancialHighlights(int recentYears, Integer limit) {
        LocalDate today = LocalDate.now();
        List<Long> companyIds = ipoEventRepository.findDistinctCompanyIdsForHomeExposure(
                today.minusMonths(HOME_EXPOSURE_PAST_MONTHS),
                today.plusWeeks(HOME_EXPOSURE_FUTURE_WEEKS)
        );

        if (limit != null && limit > 0 && companyIds.size() > limit) {
            companyIds = companyIds.subList(0, limit);
        }

        return syncAnnualFinancialHighlights(companyIds, recentYears);
    }

    private FinancialHighlightSyncResult syncAnnualFinancialHighlights(List<Long> companyIds, int recentYears) {
        int normalizedRecentYears = Math.max(1, recentYears);
        int latestBusinessYear = LocalDate.now().minusYears(1).getYear();

        List<String> synced = new ArrayList<>();
        List<String> fallbackSynced = new ArrayList<>();
        List<String> skippedSpac = new ArrayList<>();
        List<String> skippedNoData = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Set<String> syncedYearKeys = new LinkedHashSet<>();
        int attempted = 0;

        for (Long companyId : companyIds) {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

            if (isSpacCompany(company)) {
                skippedSpac.add(companyId + ":" + company.getCorpName());
                continue;
            }

            List<String> apiSkippedNoData = new ArrayList<>();

            for (int i = 0; i < normalizedRecentYears; i++) {
                String businessYear = String.valueOf(latestBusinessYear - i);

                if (syncedYearKeys.contains(yearKey(companyId, businessYear))) {
                    continue;
                }

                boolean cfsSucceeded = false;

                for (FinancialStatementType financialStatementType : FinancialStatementType.values()) {
                    if (financialStatementType == FinancialStatementType.OFS && cfsSucceeded) {
                        continue;
                    }

                    attempted++;

                    try {
                        List<CompanyFinancialHighlight> highlights = syncAnnualReportWithPriorTerms(
                                company,
                                businessYear,
                                financialStatementType,
                                normalizedRecentYears,
                                syncedYearKeys
                        );

                        for (CompanyFinancialHighlight highlight : highlights) {
                            synced.add(companyId + ":" + highlight.getBsnsYear() + ":" + financialStatementType.name());
                        }

                        if (financialStatementType == FinancialStatementType.CFS && !highlights.isEmpty()) {
                            cfsSucceeded = true;
                        }
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.FINANCIAL_DATA_NOT_FOUND) {
                            log.info(
                                    "[DartFinancialSyncService] 재무 하이라이트 데이터 없음 companyId={}, businessYear={}, fsDiv={}",
                                    companyId,
                                    businessYear,
                                    financialStatementType.name()
                            );
                            apiSkippedNoData.add(companyId + ":" + businessYear + ":" + financialStatementType.name());
                            continue;
                        }

                        log.warn(
                                "[DartFinancialSyncService] 재무 하이라이트 동기화 실패 companyId={}, businessYear={}, fsDiv={}: {}",
                                companyId,
                                businessYear,
                                financialStatementType.name(),
                                e.getMessage()
                        );
                        failed.add(companyId + ":" + businessYear + ":" + financialStatementType.name() + " - " + e.getMessage());
                    } catch (Exception e) {
                        log.warn(
                                "[DartFinancialSyncService] 재무 하이라이트 동기화 실패 companyId={}, businessYear={}, fsDiv={}: {}",
                                companyId,
                                businessYear,
                                financialStatementType.name(),
                                e.getMessage()
                        );
                        failed.add(companyId + ":" + businessYear + ":" + financialStatementType.name() + " - " + e.getMessage());
                    }
                }
            }

            if (countSyncedYears(companyId, syncedYearKeys) < normalizedRecentYears) {
                List<CompanyFinancialHighlight> fallbackHighlights = syncFinancialHighlightsFromDisclosures(
                        company,
                        normalizedRecentYears,
                        syncedYearKeys
                );

                for (CompanyFinancialHighlight highlight : fallbackHighlights) {
                    String item = companyId + ":" + highlight.getBsnsYear() + ":OFS:DISCLOSURE";
                    synced.add(item);
                    fallbackSynced.add(item);
                }
            }

            if (countSyncedYears(companyId, syncedYearKeys) < normalizedRecentYears) {
                skippedNoData.addAll(apiSkippedNoData);
            }
        }

        return FinancialHighlightSyncResult.builder()
                .targetCompanyCount(companyIds.size())
                .attemptedCount(attempted)
                .successCount(synced.size())
                .fallbackSyncedCount(fallbackSynced.size())
                .skippedSpacCount(skippedSpac.size())
                .skippedNoDataCount(skippedNoData.size())
                .failedCount(failed.size())
                .synced(synced)
                .fallbackSynced(fallbackSynced)
                .skippedSpac(skippedSpac)
                .skippedNoData(skippedNoData)
                .failed(failed)
                .build();
    }

    private List<CompanyFinancialHighlight> syncAnnualReportWithPriorTerms(
            Company company,
            String businessYear,
            FinancialStatementType financialStatementType,
            int recentYears,
            Set<String> syncedYearKeys
    ) {
        Long companyId = company.getId();

        DartFinancialStatementResponse response = dartApiClient.getFinancialStatements(
                company.getCorpCode(),
                businessYear,
                ReportCode.ANNUAL.getCode(),
                financialStatementType.name()
        );

        List<DartFinancialStatementResponse.Item> items = response.getList();

        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.FINANCIAL_DATA_NOT_FOUND);
        }

        List<CompanyFinancialHighlight> highlights = new ArrayList<>();

        for (int termOffset = 0; termOffset < 3 && highlights.size() < recentYears; termOffset++) {
            String targetBusinessYear = findBusinessYear(items, businessYear, termOffset);

            if (targetBusinessYear == null) {
                continue;
            }

            String yearKey = yearKey(companyId, targetBusinessYear);

            if (syncedYearKeys.contains(yearKey)) {
                continue;
            }

            Long revenue = findAmount(items, "매출액", termOffset);
            Long operatingProfit = findAmount(items, "영업이익", termOffset);
            Long netIncome = findAmount(items, "당기순이익", termOffset);
            Long totalAssets = findAmount(items, "자산총계", termOffset);
            Long totalLiabilities = findAmount(items, "부채총계", termOffset);
            Long totalEquity = findAmount(items, "자본총계", termOffset);

            if (!hasAnyFinancialValue(
                    revenue,
                    operatingProfit,
                    netIncome,
                    totalAssets,
                    totalLiabilities,
                    totalEquity
            )) {
                continue;
            }

            CompanyFinancialHighlight highlight = saveFinancialHighlight(
                    company,
                    targetBusinessYear,
                    ReportCode.ANNUAL,
                    financialStatementType,
                    revenue,
                    operatingProfit,
                    netIncome,
                    totalAssets,
                    totalLiabilities,
                    totalEquity,
                    findCurrency(items)
            );

            syncedYearKeys.add(yearKey);
            highlights.add(highlight);
        }

        if (highlights.isEmpty()) {
            throw new CustomException(ErrorCode.FINANCIAL_DATA_NOT_FOUND);
        }

        return highlights;
    }

    private List<CompanyFinancialHighlight> syncFinancialHighlightsFromDisclosures(
            Company company,
            int recentYears,
            Set<String> syncedYearKeys
    ) {
        List<DisclosureDocumentType> documentTypes = List.of(
                DisclosureDocumentType.INVESTMENT_PROSPECTUS,
                DisclosureDocumentType.CORRECTION_SECURITIES_REPORT,
                DisclosureDocumentType.INITIAL_SECURITIES_REPORT
        );

        List<IpoDisclosureReport> reports = disclosureReportRepository
                .findParsedIpoReportsByCompanyId(company.getId(), documentTypes)
                .stream()
                .sorted(Comparator.comparingInt(report -> report.getDocumentType().priority()))
                .toList();

        List<CompanyFinancialHighlight> highlights = new ArrayList<>();

        for (IpoDisclosureReport report : reports) {
            List<DisclosureFinancialStatementParser.ParsedFinancialHighlight> parsedHighlights =
                    disclosureFinancialStatementParser.parse(report.getOriginalText(), recentYears);

            for (DisclosureFinancialStatementParser.ParsedFinancialHighlight parsed : parsedHighlights) {
                if (highlights.size() >= recentYears) {
                    return highlights;
                }

                String yearKey = yearKey(company.getId(), parsed.getBusinessYear());

                if (syncedYearKeys.contains(yearKey)) {
                    continue;
                }

                CompanyFinancialHighlight highlight = saveFinancialHighlight(
                        company,
                        parsed.getBusinessYear(),
                        ReportCode.ANNUAL,
                        FinancialStatementType.OFS,
                        parsed.getRevenue(),
                        parsed.getOperatingProfit(),
                        parsed.getNetIncome(),
                        parsed.getTotalAssets(),
                        parsed.getTotalLiabilities(),
                        parsed.getTotalEquity(),
                        parsed.getCurrency()
                );

                syncedYearKeys.add(yearKey);
                highlights.add(highlight);
            }
        }

        return highlights;
    }

    private String yearKey(Long companyId, String businessYear) {
        return companyId + ":" + businessYear;
    }

    private int countSyncedYears(Long companyId, Set<String> syncedYearKeys) {
        String prefix = companyId + ":";
        return (int) syncedYearKeys.stream()
                .filter(key -> key.startsWith(prefix))
                .count();
    }

    private boolean isSpacCompany(Company company) {
        String corpName = company.getCorpName();
        return corpName != null && (corpName.contains("기업인수목적") || corpName.contains("스팩"));
    }

    private boolean hasAnyFinancialValue(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return true;
            }
        }

        return false;
    }

    // DART 재무제표 항목 목록에서 특정 계정명에 해당하는 금액을 찾는 메서드
    private Long findAmount(List<DartFinancialStatementResponse.Item> items, String accountNameKeyword) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        return items.stream()
                .filter(item -> item.getAccountNm() != null)
                .filter(item -> item.getAccountNm().contains(accountNameKeyword))
                .map(DartFinancialStatementResponse.Item::getThstrmAmount) // 찾은 항목에서 당기 금액 문자열만 꺼냄
                .map(ExternalNumberParser::toLong)
                .findFirst()
                .orElse(null);
    }

    private Long findAmount(List<DartFinancialStatementResponse.Item> items, String accountNameKeyword, int termOffset) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        return items.stream()
                .filter(item -> item.getAccountNm() != null)
                .filter(item -> item.getAccountNm().contains(accountNameKeyword))
                .map(item -> getAmountByTermOffset(item, termOffset))
                .map(ExternalNumberParser::toLong)
                .findFirst()
                .orElse(null);
    }

    private String getAmountByTermOffset(DartFinancialStatementResponse.Item item, int termOffset) {
        return switch (termOffset) {
            case 1 -> item.getFrmtrmAmount();
            case 2 -> item.getBfefrmtrmAmount();
            default -> item.getThstrmAmount();
        };
    }

    private String findBusinessYear(
            List<DartFinancialStatementResponse.Item> items,
            String reportBusinessYear,
            int termOffset
    ) {
        String termName = items.stream()
                .map(item -> getTermNameByOffset(item, termOffset))
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);

        if (termName != null) {
            Matcher matcher = BUSINESS_YEAR_PATTERN.matcher(termName);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        try {
            return String.valueOf(Integer.parseInt(reportBusinessYear) - termOffset);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getTermNameByOffset(DartFinancialStatementResponse.Item item, int termOffset) {
        return switch (termOffset) {
            case 1 -> item.getFrmtrmNm();
            case 2 -> item.getBfefrmtrmNm();
            default -> item.getThstrmNm();
        };
    }

    // 재무제표 항목 목록에서 통화 단위를 찾는 메서드
    private String findCurrency(List<DartFinancialStatementResponse.Item> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        return items.stream()
                .map(DartFinancialStatementResponse.Item::getCurrency)
                .filter(currency -> currency != null && !currency.isBlank())
                .findFirst()
                .orElse(null);
    }

    @Getter
    @Builder
    public static class FinancialHighlightSyncResult {
        private final int targetCompanyCount;
        private final int attemptedCount;
        private final int successCount;
        private final int fallbackSyncedCount;
        private final int skippedSpacCount;
        private final int skippedNoDataCount;
        private final int failedCount;
        private final List<String> synced;
        private final List<String> fallbackSynced;
        private final List<String> skippedSpac;
        private final List<String> skippedNoData;
        private final List<String> failed;
    }
}
