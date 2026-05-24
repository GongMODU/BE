package com.gong.modu.controller.test;

import com.gong.modu.client.DartApiClient;
import com.gong.modu.domain.dto.dart.DartDisclosureSearchResponse;
import com.gong.modu.domain.dto.dart.DartEquitySecuritiesReportResponse;
import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.domain.dto.pipeline.IpoDisclosureParsingResult;
import com.gong.modu.domain.dto.test.AiDisclosureParsingResponse;
import com.gong.modu.domain.dto.test.MultiDisclosureParsingResponse;
import com.gong.modu.domain.dto.test.MultiDisclosureParsingResponse.PerDocumentResult;
import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.service.pipeline.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Profile({"local", "local-test"})
@RestController
@RequestMapping("/api/test/disclosure-parser")
@RequiredArgsConstructor
public class DisclosureParserTestController {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DartApiClient dartApiClient;
    private final DisclosureTextExtractor disclosureTextExtractor;
    private final IpoDisclosureDocumentClassifier ipoDisclosureDocumentClassifier;
    private final DartDisclosureParsingService dartDisclosureParsingService;

    private final DisclosureContextExtractor disclosureContextExtractor; // AI 문맥 추출 담당
    private final AiDisclosureParsingService aiDisclosureParsingService; // AI 파싱 담당
    private final DisclosureParsingResultMerger disclosureParsingResultMerger; // AI 결과 검증·변환 담당
    private final IpoDisclosureReportRepository disclosureReportRepository; // 한 IPO의 공시 목록 조회

    private final DartCompanySyncService dartCompanySyncService; // 기업개황 동기화 담당
    private final DartIpoSyncService dartIpoSyncService; // 공시/주요정보 IPO 데이터 동기화 담당
    private final IpoEventRepository ipoEventRepository; // UPCOMING 일괄 재파싱용


    // 실제 DART rceptNo로 ZIP 다운로드, AI 파싱, 검증·변환 결과까지 확인하는 테스트 API
    @GetMapping("/dry-run-ai/{rceptNo}")
    public ResponseEntity<AiDisclosureParsingResponse> dryRunAiByRceptNo(
            @PathVariable String rceptNo
    ) {
        // DART 공시 원문 다운로드
        byte[] zipBytes = dartApiClient.downloadDisclosureDocumentZip(rceptNo);

        // ZIP 내부 텍스트 추출
        String originalText = disclosureTextExtractor.extractTextFromZip(zipBytes);

        // AI 파싱에 필요한 섹션 문맥 추출
        String aiContext = disclosureContextExtractor.extractContext(originalText);

        // 실제 선택된 섹션 목록도 디버깅용으로 추출
        List<AiDisclosureParsingResponse.SelectedSectionDto> selectedSections = disclosureContextExtractor
                .extractSelectedSections(originalText)
                .stream()
                .map(section -> AiDisclosureParsingResponse.SelectedSectionDto.builder()
                        .sectionType(section.getSectionType().name())
                        .matchedKeyword(section.getMatchedKeyword())
                        .startIndex(section.getStartIndex())
                        .endIndex(section.getEndIndex())
                        .contentLength(section.getContent() == null ? 0 : section.getContent().length())
                        .score(section.getScore())
                        .preview(makePreview(section.getContent()))
                        .build())
                .toList();

        // AI 파싱 결과를 담을 변수
        AiDisclosureParsingResult aiResult = null;

        // AI에게 보낼 문맥이 있을 때만 클로드 호출
        if (aiContext != null && !aiContext.isBlank()) {
            aiResult = aiDisclosureParsingService.parseWithAi(aiContext);
        }

        // AI 결과를 검증·변환
        IpoDisclosureParsingResult parsingResult = disclosureParsingResultMerger.toResult(aiResult);

        return ResponseEntity.ok(
                AiDisclosureParsingResponse.builder()
                        .rceptNo(rceptNo)
                        .originalTextLength(originalText.length())
                        .aiContextLength(aiContext == null ? 0 : aiContext.length())
                        .originalTextPreview(makePreview(originalText))
                        .aiContextPreview(makePreview(aiContext))
                        .ipoDocumentCandidate(ipoDisclosureDocumentClassifier.isIpoCandidate(originalText))
                        .detectedDocumentType(ipoDisclosureDocumentClassifier.detectDocumentType(originalText))
                        .matchedKeywords(ipoDisclosureDocumentClassifier.findMatchedIpoKeywords(originalText))
                        .aiDisclosureParsingResult(aiResult)
                        .mergedParsingResult(parsingResult)
                        .selectedSections(selectedSections)
                        .build()
        );
    }

    // 한 IPO 이벤트에 속한 모든 공시를 문서타입 우선순위 순으로 파싱하고,
    // 공시별 결과 + 전체 공시 누적 결과를 함께 반환하는 테스트 API
    // 사용 목적: "단일 공시로는 채울 수 없는 필드"가 다른 공시에서 채워지는지 검증
    @GetMapping("/dry-run-ipo/{ipoEventId}")
    public ResponseEntity<MultiDisclosureParsingResponse> dryRunByIpoEvent(
            @PathVariable Long ipoEventId
    ) {
        // 해당 IPO의 모든 공시를 문서타입 우선순위 순으로 정렬
        List<IpoDisclosureReport> reports = disclosureReportRepository.findByIpoEventId(ipoEventId)
                .stream()
                .sorted(Comparator.comparingInt((IpoDisclosureReport report) -> report.getDocumentType().priority()))
                .toList();

        // 각 공시마다 단일 파이프라인 실행
        List<PerDocumentResult> perDocumentResults = reports.stream()
                .map(report -> runReportPipeline(report.getRceptNo(), report.getReportName()))
                .toList();

        // 전체 공시에서 필드별 첫 non-null 값을 모아 누적 결과 구성
        Map<String, String> fieldSourceMap = new LinkedHashMap<>();
        IpoDisclosureParsingResult accumulatedResult = accumulate(perDocumentResults, fieldSourceMap);

        return ResponseEntity.ok(
                MultiDisclosureParsingResponse.builder()
                        .ipoEventId(ipoEventId)
                        .reportCount(perDocumentResults.size())
                        .perDocumentResults(perDocumentResults)
                        .accumulatedResult(accumulatedResult)
                        .fieldSourceMap(fieldSourceMap)
                        .build()
        );
    }

    // corp_code로 DART 공시검색 API를 호출해 한 기업의 증권신고서/투자설명서 공시를 모두 파싱하고,
    // 공시별 결과 + 전체 누적 결과를 반환하는 테스트 API (DB 시딩 불필요)
    // 사용 목적: ipo_events 데이터가 없어도 "단일 공시로는 채울 수 없는 필드"를 검증
    @GetMapping("/dry-run-corp/{corpCode}")
    public ResponseEntity<MultiDisclosureParsingResponse> dryRunByCorpCode(
            @PathVariable String corpCode,
            @RequestParam String beginDate,
            @RequestParam String endDate
    ) {
        // DART 공시검색 API로 해당 기간의 공시 목록 조회
        DartDisclosureSearchResponse search =
                dartApiClient.searchDisclosure(beginDate, endDate, corpCode, null, 1, 100);

        // IPO 관련 공시만 골라 문서타입 우선순위 순으로 정렬
        List<DartDisclosureSearchResponse.Item> items = search.getList() == null
                ? List.of()
                : search.getList().stream()
                        .filter(item -> ipoDisclosureDocumentClassifier.isIpoRelevantReportName(item.getReportNm()))
                        .sorted(Comparator.comparingInt((DartDisclosureSearchResponse.Item item) ->
                                ipoDisclosureDocumentClassifier
                                        .detectDocumentTypeFromReportName(item.getReportNm())
                                        .priority()))
                        .toList();

        // 각 공시마다 단일 파이프라인 실행
        List<PerDocumentResult> perDocumentResults = items.stream()
                .map(item -> runReportPipeline(item.getRceptNo(), item.getReportNm()))
                .toList();

        // 전체 공시에서 필드별 첫 non-null 값을 모아 누적 결과 구성
        Map<String, String> fieldSourceMap = new LinkedHashMap<>();
        IpoDisclosureParsingResult accumulatedResult = accumulate(perDocumentResults, fieldSourceMap);

        return ResponseEntity.ok(
                MultiDisclosureParsingResponse.builder()
                        .corpCode(corpCode)
                        .reportCount(perDocumentResults.size())
                        .perDocumentResults(perDocumentResults)
                        .accumulatedResult(accumulatedResult)
                        .fieldSourceMap(fieldSourceMap)
                        .build()
        );
    }

    // 단일 공시에 대해 ZIP 다운로드부터 AI 파싱·검증까지 수행하는 헬퍼 (DB 반영 없음)
    private PerDocumentResult runReportPipeline(String rceptNo, String reportName) {
        try {
            byte[] zipBytes = dartApiClient.downloadDisclosureDocumentZip(rceptNo);
            String originalText = disclosureTextExtractor.extractTextFromZip(zipBytes);

            // 문서타입은 reportName 기반 분류가 본문 키워드 분류보다 신뢰도가 높음
            DisclosureDocumentType documentType =
                    ipoDisclosureDocumentClassifier.detectDocumentTypeFromReportName(reportName);

            String aiContext = disclosureContextExtractor.extractContext(originalText);

            AiDisclosureParsingResult aiResult = null;
            if (aiContext != null && !aiContext.isBlank()) {
                aiResult = aiDisclosureParsingService.parseWithAi(aiContext);
            }

            IpoDisclosureParsingResult parsingResult = disclosureParsingResultMerger.toResult(aiResult);

            return PerDocumentResult.builder()
                    .rceptNo(rceptNo)
                    .reportName(reportName)
                    .detectedDocumentType(documentType)
                    .aiContextLength(aiContext == null ? 0 : aiContext.length())
                    .aiDisclosureParsingResult(aiResult)
                    .mergedParsingResult(parsingResult)
                    .build();
        } catch (Exception e) {
            // 공시 한 건이 실패해도 나머지 공시는 계속 처리
            return PerDocumentResult.builder()
                    .rceptNo(rceptNo)
                    .reportName(reportName)
                    .error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .build();
        }
    }

    // 공시별 병합 결과에서 필드별 첫 non-null 값을 모아 누적 결과를 구성하는 메서드
    private IpoDisclosureParsingResult accumulate(
            List<PerDocumentResult> results,
            Map<String, String> fieldSourceMap
    ) {
        return IpoDisclosureParsingResult.builder()
                .demandForecastStart(pickFirst(results, IpoDisclosureParsingResult::getDemandForecastStart, "demandForecastStart", fieldSourceMap))
                .demandForecastEnd(pickFirst(results, IpoDisclosureParsingResult::getDemandForecastEnd, "demandForecastEnd", fieldSourceMap))
                .subscriptionStart(pickFirst(results, IpoDisclosureParsingResult::getSubscriptionStart, "subscriptionStart", fieldSourceMap))
                .subscriptionEnd(pickFirst(results, IpoDisclosureParsingResult::getSubscriptionEnd, "subscriptionEnd", fieldSourceMap))
                .refundDate(pickFirst(results, IpoDisclosureParsingResult::getRefundDate, "refundDate", fieldSourceMap))
                .listingDate(pickFirst(results, IpoDisclosureParsingResult::getListingDate, "listingDate", fieldSourceMap))
                .lockupExpiryDate(pickFirst(results, IpoDisclosureParsingResult::getLockupExpiryDate, "lockupExpiryDate", fieldSourceMap))
                .offerPriceMin(pickFirst(results, IpoDisclosureParsingResult::getOfferPriceMin, "offerPriceMin", fieldSourceMap))
                .offerPriceMax(pickFirst(results, IpoDisclosureParsingResult::getOfferPriceMax, "offerPriceMax", fieldSourceMap))
                .offerPrice(pickFirst(results, IpoDisclosureParsingResult::getOfferPrice, "offerPrice", fieldSourceMap))
                .shareCount(pickFirst(results, IpoDisclosureParsingResult::getShareCount, "shareCount", fieldSourceMap))
                .totalListedShares(pickFirst(results, IpoDisclosureParsingResult::getTotalListedShares, "totalListedShares", fieldSourceMap))
                .institutionalCompetitionRate(pickFirst(results, IpoDisclosureParsingResult::getInstitutionalCompetitionRate, "institutionalCompetitionRate", fieldSourceMap))
                .lockupRatio(pickFirst(results, IpoDisclosureParsingResult::getLockupRatio, "lockupRatio", fieldSourceMap))
                .protectiveCustodyRatio(pickFirst(results, IpoDisclosureParsingResult::getProtectiveCustodyRatio, "protectiveCustodyRatio", fieldSourceMap))
                .brokerNames(pickFirst(results, IpoDisclosureParsingResult::getBrokerNames, "brokerNames", fieldSourceMap))
                .build();
    }

    // 우선순위 순으로 정렬된 공시 결과에서 특정 필드의 첫 non-null 값을 찾고 출처를 기록하는 메서드
    private <T> T pickFirst(
            List<PerDocumentResult> results,
            Function<IpoDisclosureParsingResult, T> getter,
            String fieldName,
            Map<String, String> fieldSourceMap
    ) {
        for (PerDocumentResult result : results) {
            IpoDisclosureParsingResult merged = result.getMergedParsingResult();

            if (merged == null) {
                continue;
            }

            T value = getter.apply(merged);

            if (value != null) {
                fieldSourceMap.put(fieldName, result.getRceptNo());
                return value;
            }
        }

        return null;
    }

    // 최근 IPO 공시를 발견해 기업·IPO 데이터를 DB에 동기화하는 테스트 API
    // 스케줄러 syncIpoData()를 수동으로 트리거하는 용도 (홈 화면 테스트 데이터 준비)
    // beginDate/endDate 미지정 시 최근 35일, limit 지정 시 동기화할 기업 수 제한
    @PostMapping("/sync-recent-ipos")
    public ResponseEntity<Map<String, Object>> syncRecentIpos(
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer limit
    ) {
        LocalDate today = LocalDate.now();
        String end = endDate != null ? endDate : today.format(BASIC_DATE);
        String begin = beginDate != null ? beginDate : today.minusDays(35).format(BASIC_DATE);

        // 시장 전체 발행공시에서 공모주 관련 공시를 제출한 기업 고유번호 수집
        Set<String> corpCodes = dartIpoSyncService.findRecentIpoCorpCodes(begin, end);

        List<String> synced = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int count = 0;

        for (String corpCode : corpCodes) {
            if (limit != null && count >= limit) {
                break;
            }

            try {
                // 기업 기본정보를 먼저 동기화한 뒤 공시/주요정보를 IPO 데이터로 동기화
                dartCompanySyncService.syncCompany(corpCode);
                dartIpoSyncService.syncIpoByCompany(corpCode, begin, end);
                synced.add(corpCode);
            } catch (Exception e) {
                failed.add(corpCode + ": " + e.getMessage());
            }

            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discoveredCount", corpCodes.size());
        result.put("syncedCorpCodes", synced);
        result.put("failed", failed);

        return ResponseEntity.ok(result);
    }

    // 아직 원문이 파싱되지 않은 공시를 ZIP 다운로드·AI 파싱하여 DB에 반영하는 테스트 API
    // 스케줄러 parseUnparsedDartDisclosureDocuments()를 수동으로 트리거하는 용도
    // limit은 한 번 호출에 파싱할 공시 건수 (ZIP 다운로드·AI 호출 포함이라 크면 느림)
    @PostMapping("/parse-unparsed")
    public ResponseEntity<Void> parseUnparsed(
            @RequestParam(defaultValue = "5") int limit
    ) {
        dartDisclosureParsingService.parseUnparsedDisclosureReports(limit);

        return ResponseEntity.ok().build();
    }

    // 특정 기업의 estkRs(지분증권 증권신고서 주요정보) 원응답을 그대로 확인하는 진단용 테스트 API
    // 청약기일 등이 sync 시 채워지지 않는 원인(데이터 없음/형식 등)을 파악하기 위함
    @GetMapping("/equity-report/{corpCode}")
    public ResponseEntity<Map<String, Object>> inspectEquityReport(
            @PathVariable String corpCode,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate
    ) {
        LocalDate today = LocalDate.now();
        String end = endDate != null ? endDate : today.format(BASIC_DATE);
        String begin = beginDate != null ? beginDate : today.minusDays(120).format(BASIC_DATE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("corpCode", corpCode);
        result.put("beginDate", begin);
        result.put("endDate", end);

        try {
            DartEquitySecuritiesReportResponse response =
                    dartApiClient.getEquitySecuritiesReport(corpCode, begin, end);
            result.put("dartStatus", response.getStatus());
            result.put("dartMessage", response.getMessage());
            result.put("itemCount", response.getList() == null ? 0 : response.getList().size());
            result.put("items", response.getList());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // 실제 DART rceptNo로 ZIP 다운로드, 텍스트 추출, AI 파싱, DB 반영까지 수행하는 테스트 API
    // 사용 목적
    // - DartDisclosureParsingService 전체 흐름 검증
    // - 스케줄러가 내부적으로 수행할 작업을 단건으로 먼저 테스트
    @PostMapping("/apply")
    public ResponseEntity<Void> parseAndApply(
            @RequestParam Long ipoEventId,
            @RequestParam String rceptNo
    ) {
        dartDisclosureParsingService.parseDisclosureReport(ipoEventId, rceptNo);

        return ResponseEntity.ok().build();
    }

    // 특정 IpoEvent에 속한 모든 공시를 강제로 재파싱하는 테스트 API
    // 컨텍스트 추출 로직이나 AI 프롬프트를 바꾼 뒤, 이미 original_text가 있는 공시도 다시 돌릴 때 사용
    // rceptNo만 projection으로 조회해서 LOB(original_text) 로딩 시 트랜잭션 문제 회피
    @PostMapping("/reparse-by-ipo/{ipoEventId}")
    public ResponseEntity<Map<String, Object>> reparseByIpoEvent(
            @PathVariable Long ipoEventId
    ) {
        List<String> rceptNos = disclosureReportRepository.findRceptNosByIpoEventId(ipoEventId);

        List<String> reparsed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String rceptNo : rceptNos) {
            try {
                dartDisclosureParsingService.parseDisclosureReport(ipoEventId, rceptNo);
                reparsed.add(rceptNo);
            } catch (Exception e) {
                failed.add(rceptNo + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ipoEventId", ipoEventId);
        result.put("totalReports", rceptNos.size());
        result.put("reparsed", reparsed);
        result.put("failed", failed);

        return ResponseEntity.ok(result);
    }

    // 재파싱이 필요한 모든 IPO의 공시를 일괄 재파싱하는 테스트 API
    // 조건: status가 LISTED가 아니거나, listingDate가 추정값이거나, listingDate가 NULL
    //       (= 잘못된 stale 추정값 때문에 LISTED로 잘못 마킹된 IPO도 자동 포함되어 복구됨)
    // (구 엔드포인트명 reparse-all-upcoming → reparse-all-active 로 변경; UPCOMING만 잡으면 catch-22 발생)
    @PostMapping("/reparse-all-active")
    public ResponseEntity<Map<String, Object>> reparseAllActive() {
        List<Object[]> activeProjections =
                ipoEventRepository.findIdAndCompanyNameForActiveIpos(IpoEventStatus.LISTED);

        List<Map<String, Object>> perIpoResult = new ArrayList<>();
        int totalReparsed = 0;
        int totalFailed = 0;

        for (Object[] projection : activeProjections) {
            Long ipoEventId = (Long) projection[0];
            String companyName = (String) projection[1];

            List<String> rceptNos = disclosureReportRepository.findRceptNosByIpoEventId(ipoEventId);

            List<String> reparsed = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (String rceptNo : rceptNos) {
                try {
                    dartDisclosureParsingService.parseDisclosureReport(ipoEventId, rceptNo);
                    reparsed.add(rceptNo);
                } catch (Exception e) {
                    failed.add(rceptNo + ": " + e.getMessage());
                }
            }

            totalReparsed += reparsed.size();
            totalFailed += failed.size();

            Map<String, Object> ipoResult = new LinkedHashMap<>();
            ipoResult.put("ipoEventId", ipoEventId);
            ipoResult.put("companyName", companyName);
            ipoResult.put("reparsedCount", reparsed.size());
            ipoResult.put("failedCount", failed.size());
            if (!failed.isEmpty()) {
                ipoResult.put("failed", failed);
            }
            perIpoResult.add(ipoResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeIpoCount", activeProjections.size());
        result.put("totalReparsed", totalReparsed);
        result.put("totalFailed", totalFailed);
        result.put("perIpo", perIpoResult);

        return ResponseEntity.ok(result);
    }

    // 원문의 앞부분 일부만 잘라서 미리보기로 반환하는 메서드
    private String makePreview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int previewLength = Math.min(text.length(), 1000);

        return text.substring(0, previewLength);
    }

}
