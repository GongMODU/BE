package com.gong.modu.service.ipo;

import com.gong.modu.domain.dto.ipo.IpoDetailResponse;
import com.gong.modu.domain.entity.ipo.*;
import com.gong.modu.domain.enums.ipo.FinancialStatementType;
import com.gong.modu.domain.enums.ipo.ReportCode;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.CompanyFinancialHighlightRepository;
import com.gong.modu.repository.ipo.IpoEventBrokerRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IpoDetailQueryService {

    private final IpoEventRepository ipoEventRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;
    private final CompanyFinancialHighlightRepository financialRepository;
    private final IpoSignalCalculator ipoSignalCalculator;

    @Transactional(readOnly = true)
    public IpoDetailResponse getDetail(Long ipoEventId) {
        IpoEvent event = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        IpoOffering offering = event.getOffering();
        IpoMetric metric = event.getMetric();

        IpoSignalCalculator.SignalResult signal = ipoSignalCalculator.calculate(metric);

        List<String> brokerNames = ipoEventBrokerRepository.findByIpoEventId(ipoEventId).stream()
                .map(IpoEventBroker::getBrokerName)
                .distinct()
                .toList();

        CompanyFinancialHighlight latestFinancial = resolveLatestFinancial(event.getCompany().getId());

        return IpoDetailResponse.builder()
                .ipoEventId(event.getId())
                .companyName(event.getCompany().getCorpName())
                .signalLevel(signal.signalLevel())
                .riskScore(signal.riskScore())
                .subscription(buildSubscriptionTab(event, offering, metric))
                .forecast(buildForecastTab(event, offering, metric))
                .company(buildCompanyTab(offering, metric, latestFinancial, brokerNames))
                .build();
    }

    private IpoDetailResponse.SubscriptionTab buildSubscriptionTab(IpoEvent event, IpoOffering offering, IpoMetric metric) {
        return IpoDetailResponse.SubscriptionTab.builder()
                .subscriptionStartDate(event.getSubscriptionStartDate())
                .subscriptionEndDate(event.getSubscriptionEndDate())
                .listingDate(event.getListingDate())
                .refundDate(event.getRefundDate())
                .generalSubscriptionRate(metric != null ? metric.getGeneralSubscriptionRate() : null)
                .proportionalCompetitionRate(metric != null ? metric.getProportionalCompetitionRate() : null)
                .equalAllocationShares(offering != null ? offering.getEqualAllocationShares() : null)
                .generalAllocationShares(offering != null ? offering.getGeneralAllocationShares() : null)
                .build();
    }

    private IpoDetailResponse.ForecastTab buildForecastTab(IpoEvent event, IpoOffering offering, IpoMetric metric) {
        return IpoDetailResponse.ForecastTab.builder()
                .offerPrice(offering != null ? offering.getOfferPrice() : null)
                .offerPriceMin(offering != null ? offering.getOfferPriceMin() : null)
                .offerPriceMax(offering != null ? offering.getOfferPriceMax() : null)
                .demandForecastStart(event.getDemandForecastStart())
                .demandForecastEnd(event.getDemandForecastEnd())
                .lockupRatio(metric != null ? metric.getLockupRatio() : null)
                .institutionalCompetitionRate(metric != null ? metric.getInstitutionalCompetitionRate() : null)
                .build();
    }

    private IpoDetailResponse.CompanyTab buildCompanyTab(
            IpoOffering offering,
            IpoMetric metric,
            CompanyFinancialHighlight financial,
            List<String> brokerNames
    ) {
        return IpoDetailResponse.CompanyTab.builder()
                .revenue(financial != null ? financial.getRevenue() : null)
                .netIncome(financial != null ? financial.getNetIncome() : null)
                .shareCount(offering != null ? offering.getShareCount() : null)
                .totalListedShares(offering != null ? offering.getTotalListedShares() : null)
                .protectiveCustodyRatio(metric != null ? metric.getProtectiveCustodyRatio() : null)
                .brokerNames(brokerNames)
                .build();
    }

    private CompanyFinancialHighlight resolveLatestFinancial(Long companyId) {
        List<CompanyFinancialHighlight> annuals =
                financialRepository.findByCompanyIdAndReportCodeOrderByBsnsYearDesc(companyId, ReportCode.ANNUAL);

        Map<String, CompanyFinancialHighlight> byYear = annuals.stream()
                .collect(Collectors.toMap(
                        CompanyFinancialHighlight::getBsnsYear,
                        h -> h,
                        (existing, incoming) ->
                                existing.getFinancialStatementType() == FinancialStatementType.CFS ? existing : incoming
                ));

        return byYear.values().stream()
                .max(Comparator.comparing(CompanyFinancialHighlight::getBsnsYear))
                .orElse(null);
    }

}
