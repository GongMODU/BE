package com.gong.modu.service.ipo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gong.modu.constant.SummaryPrompts;
import com.gong.modu.domain.dto.anthropic.AnthropicMessageDto;
import com.gong.modu.domain.dto.ipo.IpoSummaryResult;
import com.gong.modu.domain.entity.ipo.*;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.CompanyFinancialHighlightRepository;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventBrokerRepository;
import com.gong.modu.service.AnthropicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpoDisclosureReportSummarizeService {

    private final IpoDisclosureReportRepository reportRepository;
    private final IpoEventBrokerRepository eventBrokerRepository;
    private final CompanyFinancialHighlightRepository financialHighlightRepository;
    private final AnthropicService anthropicService;
    private final ObjectMapper objectMapper;

    private static final int MAX_TOKENS = 2000;

    @Transactional
    public void summarize(Long reportId) {
        IpoDisclosureReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        IpoEvent ipoEvent = report.getIpoEvent();
        Company company = ipoEvent.getCompany();
        IpoOffering offering = ipoEvent.getOffering();
        IpoMetric metric = ipoEvent.getMetric();

        boolean isSpac = company.isSpac();

        List<IpoEventBroker> brokers = eventBrokerRepository.findByIpoEventId(ipoEvent.getId());
        List<CompanyFinancialHighlight> financials = financialHighlightRepository
                .findByCompanyIdOrderByBsnsYearDesc(company.getId())
                .stream().limit(2).toList();

        String inputData = buildInputData(company, ipoEvent, offering, metric, financials, brokers, isSpac);

        // AnthropicRequestDto에 system 필드가 없으므로 시스템 프롬프트를 유저 메시지 앞에 합쳐서 전달
        String prompt = SummaryPrompts.IPO_SYSTEM_PROMPT + "\n\n"
                + SummaryPrompts.IPO_USER_PROMPT_TEMPLATE.replace("{inputData}", inputData);

        List<AnthropicMessageDto> messages = List.of(new AnthropicMessageDto("user", prompt));

        String responseJson = anthropicService.call(messages, MAX_TOKENS);

        IpoSummaryResult result = parseResponse(responseJson, reportId);
        if (result == null) return;

        report.updateSummary(
                serializeStringList(result.getCompanySummary()),
                serializeStringList(result.getFinancialSummary()),
                serializeSummarySection(result.getInvestorProtectionSummary()),
                serializeSummarySection(result.getMergerInfoSummary()),
                serializeRiskItems(result.getRiskSummary()),
                SummaryPrompts.IPO_SUMMARY_VERSION
        );
    }

    private String buildInputData(
            Company company,
            IpoEvent ipoEvent,
            IpoOffering offering,
            IpoMetric metric,
            List<CompanyFinancialHighlight> financials,
            List<IpoEventBroker> brokers,
            boolean isSpac
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 기업 정보\n");
        sb.append("- 기업명: ").append(company.getCorpName()).append("\n");
        if (company.getCorpNameEng() != null) {
            sb.append("- 영문명: ").append(company.getCorpNameEng()).append("\n");
        }
        sb.append("- 기업 유형: ").append(isSpac ? "SPAC (기업인수목적회사)" : "일반 공모주").append("\n");
        if (company.getCorpClass() != null) {
            sb.append("- 법인 구분: ").append(company.getCorpClass()).append("\n");
        }
        if (company.getEstablishedAt() != null) {
            sb.append("- 설립일: ").append(company.getEstablishedAt()).append("\n");
        }
        if (company.getMarketType() != null) {
            sb.append("- 상장 시장: ").append(company.getMarketType()).append("\n");
        }

        sb.append("\n## 공모 일정\n");
        if (ipoEvent.getDemandForecastStart() != null) {
            sb.append("- 수요예측 시작일: ").append(ipoEvent.getDemandForecastStart()).append("\n");
        }
        if (ipoEvent.getDemandForecastEnd() != null) {
            sb.append("- 수요예측 종료일: ").append(ipoEvent.getDemandForecastEnd()).append("\n");
        }
        if (ipoEvent.getSubscriptionStartDate() != null) {
            sb.append("- 청약 시작일: ").append(ipoEvent.getSubscriptionStartDate()).append("\n");
        }
        if (ipoEvent.getSubscriptionEndDate() != null) {
            sb.append("- 청약 종료일: ").append(ipoEvent.getSubscriptionEndDate()).append("\n");
        }
        if (ipoEvent.getRefundDate() != null) {
            sb.append("- 환불일: ").append(ipoEvent.getRefundDate()).append("\n");
        }
        if (ipoEvent.getListingDate() != null) {
            sb.append("- 상장일: ").append(ipoEvent.getListingDate()).append("\n");
        }
        if (ipoEvent.getStatus() != null) {
            sb.append("- 공모 상태: ").append(ipoEvent.getStatus()).append("\n");
        }

        if (offering != null) {
            boolean hasOfferingData = offering.getOfferPriceMin() != null
                    || offering.getOfferPriceMax() != null
                    || offering.getOfferPrice() != null
                    || offering.getShareCount() != null
                    || offering.getTotalListedShares() != null;
            if (hasOfferingData) {
                sb.append("\n## 공모 조건\n");
                if (offering.getOfferPriceMin() != null) {
                    sb.append("- 희망 공모가 하단: ").append(offering.getOfferPriceMin()).append("원\n");
                }
                if (offering.getOfferPriceMax() != null) {
                    sb.append("- 희망 공모가 상단: ").append(offering.getOfferPriceMax()).append("원\n");
                }
                if (offering.getOfferPrice() != null) {
                    sb.append("- 확정 공모가: ").append(offering.getOfferPrice()).append("원\n");
                }
                if (offering.getShareCount() != null) {
                    sb.append("- 공모 주식 수: ").append(offering.getShareCount()).append("주\n");
                }
                if (offering.getTotalListedShares() != null) {
                    sb.append("- 상장 후 총 주식 수: ").append(offering.getTotalListedShares()).append("주\n");
                }
            }
        }

        if (metric != null) {
            boolean hasMetricData = metric.getInstitutionalCompetitionRate() != null
                    || metric.getLockupRatio() != null
                    || metric.getGeneralSubscriptionRate() != null
                    || metric.getProportionalCompetitionRate() != null
                    || metric.getProtectiveCustodyRatio() != null
                    || metric.getCirculatingSharesRatio() != null
                    || metric.getSignalLevel() != null;
            if (hasMetricData) {
                sb.append("\n## 공모 지표\n");
                if (metric.getInstitutionalCompetitionRate() != null) {
                    sb.append("- 기관경쟁률: ").append(metric.getInstitutionalCompetitionRate()).append(":1\n");
                }
                if (metric.getGeneralSubscriptionRate() != null) {
                    sb.append("- 일반청약 경쟁률: ").append(metric.getGeneralSubscriptionRate()).append(":1\n");
                }
                if (metric.getProportionalCompetitionRate() != null) {
                    sb.append("- 비례배정 경쟁률: ").append(metric.getProportionalCompetitionRate()).append(":1\n");
                }
                if (metric.getLockupRatio() != null) {
                    sb.append("- 의무보유확약 비율: ")
                            .append(metric.getLockupRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString())
                            .append("%\n");
                }
                if (metric.getProtectiveCustodyRatio() != null) {
                    sb.append("- 보호예수 비율: ")
                            .append(metric.getProtectiveCustodyRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString())
                            .append("%\n");
                }
                if (metric.getCirculatingSharesRatio() != null) {
                    sb.append("- 유통가능물량 비율: ")
                            .append(metric.getCirculatingSharesRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString())
                            .append("%\n");
                }
                if (metric.getSignalLevel() != null) {
                    sb.append("- 신호등 등급: ").append(metric.getSignalLevel()).append("\n");
                }
            }
        }

        if (!financials.isEmpty()) {
            String currency = financials.get(0).getCurrency();
            sb.append("\n## 재무 정보 (최신 2개년)");
            if (currency != null) {
                sb.append(" (단위: ").append(currency).append(")");
            }
            sb.append("\n");
            for (CompanyFinancialHighlight f : financials) {
                sb.append("### ").append(f.getBsnsYear()).append("년\n");
                if (f.getRevenue() != null) {
                    sb.append("- 매출액: ").append(f.getRevenue()).append("\n");
                }
                if (f.getOperatingProfit() != null) {
                    sb.append("- 영업이익: ").append(f.getOperatingProfit()).append("\n");
                }
                if (f.getNetIncome() != null) {
                    sb.append("- 당기순이익: ").append(f.getNetIncome()).append("\n");
                }
                if (f.getTotalAssets() != null) {
                    sb.append("- 자산총계: ").append(f.getTotalAssets()).append("\n");
                }
                if (f.getTotalLiabilities() != null) {
                    sb.append("- 부채총계: ").append(f.getTotalLiabilities()).append("\n");
                }
                if (f.getTotalEquity() != null) {
                    sb.append("- 자본총계: ").append(f.getTotalEquity()).append("\n");
                }
            }
        }

        if (!brokers.isEmpty()) {
            sb.append("\n## 인수기관\n");
            brokers.forEach(b -> sb.append("- ").append(b.getBrokerName()).append("\n"));
        }

        return sb.toString();
    }

    private IpoSummaryResult parseResponse(String json, Long reportId) {
        try {
            return objectMapper.readValue(json, IpoSummaryResult.class);
        } catch (JsonProcessingException e) {
            log.warn("[IpoSummarize] 응답 파싱 실패 reportId={}: {}", reportId, e.getMessage());
            return null;
        }
    }

    private String serializeSummarySection(IpoSummaryResult.SummarySection section) {
        if (section == null) return null;
        try {
            return objectMapper.writeValueAsString(section);
        } catch (JsonProcessingException e) {
            log.warn("[IpoSummarize] SummarySection 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    private String serializeStringList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            log.warn("[IpoSummarize] 문장 배열 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    private String serializeRiskItems(List<IpoSummaryResult.RiskItem> items) {
        if (items == null || items.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.warn("[IpoSummarize] riskSummary 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
