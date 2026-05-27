package com.gong.modu.service.ipo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gong.modu.domain.dto.ipo.IpoDisclosureReportResponse;
import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpoDisclosureReportQueryService {

    private final IpoDisclosureReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public IpoDisclosureReportResponse getDisclosureReport(Long ipoEventId) {
        List<IpoDisclosureReport> reports = reportRepository.findByIpoEventId(ipoEventId);

        if (reports.isEmpty()) {
            throw new CustomException(ErrorCode.DISCLOSURE_REPORT_NOT_FOUND);
        }

        IpoDisclosureReport report = reports.get(0);

        if (report.getCompanySummary() == null) {
            return IpoDisclosureReportResponse.builder()
                    .summaryVersion(report.getSummaryVersion())
                    .build();
        }

        return IpoDisclosureReportResponse.builder()
                .companySummary(report.getCompanySummary())
                .financialSummary(report.getFinancialSummary())
                .investorProtectionSummary(deserializeSummarySection(report.getInvestorProtectionSummary()))
                .investmentPointSummary(deserializeSummarySection(report.getInvestmentPointSummary()))
                .riskSummary(deserializeRiskItems(report.getRiskSummary()))
                .summaryVersion(report.getSummaryVersion())
                .build();
    }

    private IpoDisclosureReportResponse.SummarySection deserializeSummarySection(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, IpoDisclosureReportResponse.SummarySection.class);
        } catch (Exception e) {
            log.warn("SummarySection 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    private List<IpoDisclosureReportResponse.RiskItem> deserializeRiskItems(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<IpoDisclosureReportResponse.RiskItem>>() {});
        } catch (Exception e) {
            log.warn("RiskItem 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
