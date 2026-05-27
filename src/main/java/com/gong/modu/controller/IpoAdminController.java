package com.gong.modu.controller;

import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.FinancialStatementType;
import com.gong.modu.domain.enums.ipo.ReportCode;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.service.ipo.IpoDisclosureReportSummarizeService;
import com.gong.modu.service.pipeline.DartFinancialSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class IpoAdminController {

    private final IpoDisclosureReportRepository reportRepository;
    private final IpoDisclosureReportSummarizeService summarizeService;
    private final DartFinancialSyncService dartFinancialSyncService;
    private final IpoEventRepository ipoEventRepository;

    // 특정 공모 이벤트의 공시 요약을 즉시 강제 재생성하는 관리자용 API
    // 기존 summary 데이터가 있어도 덮어씀
    @PostMapping("/admin/ipo/{ipoEventId}/summarize")
    public Map<String, String> summarize(@PathVariable Long ipoEventId) {
        List<IpoDisclosureReport> reports = reportRepository.findByIpoEventId(ipoEventId);

        if (reports.isEmpty()) {
            return Map.of("message", "해당 공모 이벤트의 공시 데이터가 없습니다.");
        }

        int success = 0;
        int fail = 0;

        for (IpoDisclosureReport report : reports) {
            try {
                summarizeService.summarize(report.getId());
                success++;
            } catch (Exception e) {
                log.warn("[IpoAdmin] 요약 실패 reportId={}: {}", report.getId(), e.getMessage());
                fail++;
            }
        }

        return Map.of("message", "요약 완료 (성공: " + success + "건, 실패: " + fail + "건)");
    }

    // 특정 공모 이벤트의 기업 재무 하이라이트를 DART에서 동기화하는 관리자용 API
    // years: 동기화할 사업연도 목록 (예: 2023,2024)
    // CFS(연결) 우선, 실패 시 OFS(개별) 시도
    @PostMapping("/admin/ipo/{ipoEventId}/financial-sync")
    public Map<String, String> syncFinancials(
            @PathVariable Long ipoEventId,
            @RequestParam List<String> years
    ) {
        Long companyId = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND))
                .getCompany().getId();

        int success = 0;
        int fail = 0;

        for (String year : years) {
            for (FinancialStatementType fsType : List.of(FinancialStatementType.CFS, FinancialStatementType.OFS)) {
                try {
                    dartFinancialSyncService.syncFinancialHighlight(companyId, year, ReportCode.ANNUAL, fsType);
                    success++;
                } catch (Exception e) {
                    log.warn("[IpoAdmin] 재무 동기화 실패 companyId={}, year={}, type={}: {}", companyId, year, fsType, e.getMessage());
                    fail++;
                }
            }
        }

        return Map.of("message", "재무 동기화 완료 (성공: " + success + "건, 실패: " + fail + "건)");
    }
}
