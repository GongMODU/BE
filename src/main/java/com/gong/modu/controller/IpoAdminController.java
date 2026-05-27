package com.gong.modu.controller;

import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.service.ipo.IpoDisclosureReportSummarizeService;
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

    // DB에 저장된 모든 공시 리포트의 AI 요약을 일괄 재생성하는 관리자용 API
    // 프롬프트 구조 변경 후 기존 데이터를 최신 형식으로 마이그레이션할 때 사용
    @PostMapping("/admin/ipo/summarize-all")
    public Map<String, String> summarizeAll() {
        List<IpoDisclosureReport> all = reportRepository.findAll();

        int success = 0;
        int fail = 0;

        for (IpoDisclosureReport report : all) {
            try {
                summarizeService.summarize(report.getId());
                success++;
            } catch (Exception e) {
                log.warn("[IpoAdmin] 일괄 요약 실패 reportId={}: {}", report.getId(), e.getMessage());
                fail++;
            }
        }

        return Map.of("message", "일괄 요약 완료 (성공: " + success + "건, 실패: " + fail + "건)");
    }

}
