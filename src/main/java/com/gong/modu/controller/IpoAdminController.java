package com.gong.modu.controller;

import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.FinancialStatementType;
import com.gong.modu.domain.enums.ipo.ReportCode;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.service.ipo.IpoDisclosureReportSummarizeService;
import com.gong.modu.service.pipeline.DartCompanySyncService;
import com.gong.modu.service.pipeline.DartDisclosureParsingService;
import com.gong.modu.service.pipeline.DartFinancialSyncService;
import com.gong.modu.service.pipeline.DartIpoSyncService;
import com.gong.modu.util.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// IPO 운영 관리자용 컨트롤러
// 모든 endpoint 는 X-IPO-ADMIN-KEY 헤더 필수 (IpoAdminApiKeyFilter 가 검증)
// 새벽 스케줄러 외에 운영 환경에서 수동 트리거가 필요한 작업들을 모은다.
@Tag(name = "IpoAdmin", description = "IPO 운영 관리자 전용 (X-IPO-ADMIN-KEY 헤더 필수)")
@Slf4j
@RestController
@RequiredArgsConstructor
public class IpoAdminController {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final IpoDisclosureReportRepository reportRepository;
    private final IpoDisclosureReportSummarizeService summarizeService;
    private final DartIpoSyncService dartIpoSyncService;
    private final DartCompanySyncService dartCompanySyncService;
    private final DartDisclosureParsingService dartDisclosureParsingService;
    private final DartFinancialSyncService dartFinancialSyncService;

    private final RedisUtil redisUtil;

    private final IpoEventRepository ipoEventRepository;


    @Operation(
            summary = "특정 IPO의 공시 요약 강제 재생성",
            description = "기존 summary 데이터를 덮어쓰며 재요약. 요약 알고리즘 변경 후 수동 트리거 용도."
    )
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

    @Operation(
            summary = "전체 공시 리포트 AI 요약 일괄 재생성",
            description = "DB에 저장된 모든 공시 리포트를 순회하며 재요약. 프롬프트 구조 변경 후 기존 데이터 마이그레이션 용도."
    )
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

    @Operation(
            summary = "최근 IPO 공시 발견·기업·IPO 데이터 동기화",
            description = """
                    DART 시장 전체 공시에서 공모주 관련 기업을 찾아 기업 정보 + IPO 메타 데이터를 DB에 동기화한다.
                    스케줄러 syncIpoData()(매일 새벽 1시) 와 동일 작업의 수동 트리거 버전.
                    beginDate/endDate 미지정 시 최근 35일, limit 지정 시 동기화 기업 수 제한.
                    """
    )
    @PostMapping("/api/admin/ipo/sync-recent-ipos")
    public ResponseEntity<Map<String, Object>> syncRecentIpos(
            @Parameter(description = "조회 시작일 (yyyyMMdd, 미지정 시 35일 전)")
            @RequestParam(required = false) String beginDate,
            @Parameter(description = "조회 종료일 (yyyyMMdd, 미지정 시 오늘)")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "동기화할 최대 기업 수 (미지정 시 발견된 전부)")
            @RequestParam(required = false) Integer limit
    ) {
        LocalDate today = LocalDate.now();
        String end = endDate != null ? endDate : today.format(BASIC_DATE);
        String begin = beginDate != null ? beginDate : today.minusDays(35).format(BASIC_DATE);

        Set<String> corpCodes = dartIpoSyncService.findRecentIpoCorpCodes(begin, end);

        List<String> synced = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int count = 0;

        for (String corpCode : corpCodes) {
            if (limit != null && count >= limit) break;
            try {
                dartCompanySyncService.syncCompany(corpCode);
                dartIpoSyncService.syncIpoByCompany(corpCode, begin, end);
                synced.add(corpCode);
            } catch (Exception e) {
                log.warn("[IpoAdmin] sync 실패 corpCode={}: {}", corpCode, e.getMessage());
                failed.add(corpCode + ": " + e.getMessage());
            }
            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discoveredCount", corpCodes.size());
        result.put("syncedCount", synced.size());
        result.put("failedCount", failed.size());
        result.put("syncedCorpCodes", synced);
        if (!failed.isEmpty()) result.put("failed", failed);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "미파싱 공시 ZIP 다운로드 + AI 파싱",
            description = """
                    original_text 가 NULL 인 공시들을 limit 만큼 가져와 ZIP 다운로드 → AI 파싱 → DB 반영.
                    스케줄러 parseUnparsedDartDisclosureDocuments()(새벽 2시, 오전 8시) 와 동일.
                    Claude 크레딧 충전 직후 등 수동 보강 용도.
                    """
    )
    @PostMapping("/api/admin/ipo/parse-unparsed")
    public ResponseEntity<Map<String, Object>> parseUnparsed(
            @Parameter(description = "한 번에 파싱할 미파싱 공시 건수 (기본 50)")
            @RequestParam(defaultValue = "50") int limit
    ) {
        dartDisclosureParsingService.parseUnparsedDisclosureReports(limit);
        return ResponseEntity.ok(Map.of(
                "message", "미파싱 공시 파싱 시도 완료 (limit=" + limit + ")"
        ));
    }

    @Operation(
            summary = "특정 기업 재무 하이라이트 수동 동기화",
            description = """
                    특정 companyId 의 최근 N개년 사업보고서(ANNUAL) 재무 하이라이트를 DART 에서 가져와 저장/갱신한다.
                    연결(CFS)과 개별(OFS)을 모두 시도하며, 조회 API 는 연결 우선/개별 폴백으로 사용한다.
                    """
    )
    @PostMapping("/api/admin/ipo/financial-highlights/sync-company/{companyId}")
    public ResponseEntity<DartFinancialSyncService.FinancialHighlightSyncResult> syncCompanyFinancialHighlights(
            @PathVariable Long companyId,
            @Parameter(description = "최근 몇 개 사업연도를 동기화할지 (기본 3)")
            @RequestParam(defaultValue = "3") int recentYears
    ) {
        return ResponseEntity.ok(
                dartFinancialSyncService.syncCompanyAnnualFinancialHighlights(companyId, recentYears)
        );
    }

    @Operation(
            summary = "홈 노출 IPO 기업 재무 하이라이트 일괄 수동 동기화",
            description = """
                    홈/상세 화면 노출 범위(최근 3개월 ~ 향후 4주 주요 일정)에 들어오는 IPO 기업들의 최근 N개년 사업보고서 재무 하이라이트를 저장/갱신한다.
                    스케줄러 syncActiveIpoFinancialHighlights()(매일 새벽 4시) 와 동일 작업.
                    """
    )
    @PostMapping("/api/admin/ipo/financial-highlights/sync-active")
    public ResponseEntity<DartFinancialSyncService.FinancialHighlightSyncResult> syncActiveIpoFinancialHighlights(
            @Parameter(description = "최근 몇 개 사업연도를 동기화할지 (기본 3)")
            @RequestParam(defaultValue = "3") int recentYears,
            @Parameter(description = "동기화할 최대 기업 수 (미지정 시 대상 전체)")
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(
                dartFinancialSyncService.syncActiveIpoAnnualFinancialHighlights(recentYears, limit)
        );
    }

    @Operation(
            summary = "활성 IPO 일괄 재파싱",
            description = """
                    status != LISTED 이거나 listingDate 가 추정값/NULL 인 모든 IPO 공시를 일괄 재파싱.
                    스케줄러 reparseActiveIpos()(새벽 3시) 와 동일 작업.
                    AI 프롬프트나 추출 로직 개선 후 기존 데이터 보정 용도.
                    """
    )
    @PostMapping("/api/admin/ipo/reparse-all-active")
    public ResponseEntity<Map<String, String>> reparseAllActive() {
        dartDisclosureParsingService.reparseAllActiveIpos();
        return ResponseEntity.ok(Map.of("message", "활성 IPO 일괄 재파싱 시도 완료"));
    }

    @Operation(
            summary = "특정 IpoEvent 공시 전체 강제 재파싱",
            description = "해당 IPO 의 모든 rceptNo 를 순회하며 다시 파싱. 특정 IPO 만 보정할 때."
    )
    @PostMapping("/api/admin/ipo/reparse-by-event/{ipoEventId}")
    public ResponseEntity<Map<String, Object>> reparseByEvent(@PathVariable Long ipoEventId) {
        List<String> rceptNos = reportRepository.findRceptNosByIpoEventId(ipoEventId);

        List<String> reparsed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String rceptNo : rceptNos) {
            try {
                dartDisclosureParsingService.parseDisclosureReport(ipoEventId, rceptNo);
                reparsed.add(rceptNo);
            } catch (Exception e) {
                log.warn("[IpoAdmin] reparse 실패 rceptNo={}: {}", rceptNo, e.getMessage());
                failed.add(rceptNo + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ipoEventId", ipoEventId);
        result.put("totalReports", rceptNos.size());
        result.put("reparsedCount", reparsed.size());
        result.put("failedCount", failed.size());
        result.put("reparsed", reparsed);
        if (!failed.isEmpty()) result.put("failed", failed);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "공시 파싱 실패 캐시 초기화",
            description = """
                    DART 연결 버그 수정 후 6시간 TTL 만료 전에 즉시 재시도하고 싶을 때 사용.
                    rceptNos 파라미터가 없으면 전체 실패 캐시를 삭제.
                    rceptNos 지정 시 해당 공시만 초기화.
                    """
    )
    @PostMapping("/api/admin/ipo/clear-parse-failure-cache")
    public ResponseEntity<Map<String, Object>> clearParseFailureCache(
            @Parameter(description = "초기화할 접수번호 목록 (미지정 시 전체 초기화)")
            @RequestParam(required = false) List<String> rceptNos
    ) {
        int cleared = (rceptNos == null || rceptNos.isEmpty())
                ? redisUtil.clearAllDisclosureParsingFailedKeys()
                : redisUtil.clearDisclosureParsingFailedKeys(rceptNos);

        return ResponseEntity.ok(Map.of(
                "clearedCount", cleared,
                "message", "공시 파싱 실패 캐시 " + cleared + "건 초기화 완료"
        ));
    }

    @Operation(
            summary = "IPO 상태 일괄 재계산",
            description = """
                    모든 IpoEvent 의 status 를 오늘 날짜 기준으로 다시 계산해 DB 에 반영.
                    스케줄러 refreshIpoStatuses()(매일 00:30) 와 동일 작업. UPCOMING↔ONGOING↔CLOSED↔LISTED 전이 보정.
                    """
    )
    @PostMapping("/api/admin/ipo/refresh-statuses")
    public ResponseEntity<Map<String, Object>> refreshStatuses() {
        int updated = dartIpoSyncService.refreshAllIpoStatuses();
        return ResponseEntity.ok(Map.of(
                "updatedCount", updated,
                "message", "IPO 상태 재계산 완료 (" + updated + " 건 변경)"
        ));
    }

    @Operation(
            summary = "특정 IPO 기업 재무 하이라이트 DART 동기화",
            description = "years 파라미터로 지정한 사업연도의 재무 데이터를 DART에서 가져와 DB에 저장. CFS(연결) → OFS(개별) 순서로 시도."
    )
    @PostMapping("/admin/ipo/{ipoEventId}/financial-sync")
    public Map<String, String> syncFinancials(
            @PathVariable Long ipoEventId,
            @Parameter(description = "동기화할 사업연도 목록 (예: 2023,2024)")
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
