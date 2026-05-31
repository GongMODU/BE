package com.gong.modu.scheduler;

import com.gong.modu.domain.entity.ipo.Company;
import com.gong.modu.repository.ipo.CompanyRepository;
import com.gong.modu.service.pipeline.DartCompanySyncService;
import com.gong.modu.service.pipeline.DartDisclosureParsingService;
import com.gong.modu.service.pipeline.DartFinancialSyncService;
import com.gong.modu.service.pipeline.DartIpoSyncService;
import com.gong.modu.service.pipeline.KisStockPriceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j // 배치 시작/종료/실패 로그를 위함
@Component
@RequiredArgsConstructor
// 외부 API 데이터 동기화를 주기적으로 실행하는 스케줄러 클래스
public class ExternalDataScheduler {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // IPO 동기화 시 공시를 검색할 과거 조회 범위 (일 단위)
    // 한국 IPO는 원본 증권신고서 제출 → 1~2개월 후 정정/효력발생/상장으로 진행되므로
    // 정정 공시만 잡고 원본 신고서를 놓치면 ZIP 원문이 없는 정정 공시만 DB에 남게 됨.
    // 6개월 범위로 잡아 원본 신고서까지 함께 수집해야 공시 원문 + 재무제표 fallback이 동작함.
    private static final int IPO_SYNC_LOOKBACK_DAYS = 180;

    private final CompanyRepository companyRepository;
    private final KisStockPriceSyncService kisStockPriceSyncService; // 실제 KIS 주가 동기화 로직을 실행할 서비스

    private final DartCompanySyncService dartCompanySyncService; // DART 기업개황을 companies 테이블에 동기화하는 서비스
    private final DartIpoSyncService dartIpoSyncService; // DART 공시/주요정보를 IPO 데이터로 동기화하는 서비스
    private final DartDisclosureParsingService dartDisclosureParsingService; // DART 공시 원문 다운/파싱 로직을 실행하는 서비스
    private final DartFinancialSyncService dartFinancialSyncService; // DART 재무제표 기반 재무 하이라이트 동기화 서비스

    // AI 파이프라인(Claude 호출) 스케줄러 on/off 토글.
    // 발표 기간 등 토큰 비용 일시 차단이 필요할 때 환경변수 SCHEDULER_AI_PIPELINES_ENABLED=false 로 끄고
    // 발표 후 true 로 되돌리거나 환경변수를 제거하면 기본값 true 로 복원된다.
    // 영향 대상: parseUnparsedDartDisclosureDocuments (02:00, 08:00), reparseActiveIpos (03:00)
    // 영향 외:   syncIpoData / refreshIpoStatuses / syncActiveIpoFinancialHighlights / syncListedCompanyPrices
    @Value("${scheduler.ai-pipelines.enabled:true}")
    private boolean aiPipelinesEnabled;

    // 시장 전체에서 최근 공모주 관련 공시를 찾아 기업·IPO 데이터를 동기화하는 스케줄러 메서드
    // 공시 원문 파싱 스케줄러(새벽 2시)보다 먼저 실행되도록 새벽 1시로 설정
    @Scheduled(cron = "0 0 1 * * *")
    public void syncIpoData() {
        log.info("[Scheduler] DART IPO 데이터 동기화 시작");

        LocalDate today = LocalDate.now();
        String endDate = today.format(BASIC_DATE);
        String beginDate = today.minusDays(IPO_SYNC_LOOKBACK_DAYS).format(BASIC_DATE);

        try {
            // 시장 전체 발행공시에서 공모주 관련 공시를 제출한 기업 고유번호 수집
            Set<String> corpCodes = dartIpoSyncService.findRecentIpoCorpCodes(beginDate, endDate);

            for (String corpCode : corpCodes) {
                try {
                    // 기업 기본정보를 먼저 동기화한 뒤 (syncIpoByCompany가 Company 존재를 전제로 함)
                    dartCompanySyncService.syncCompany(corpCode);

                    // 해당 기업의 공시/주요정보를 IPO 데이터로 동기화
                    dartIpoSyncService.syncIpoByCompany(corpCode, beginDate, endDate);
                } catch (Exception e) {
                    // 특정 기업 동기화 실패가 전체 배치를 중단시키지 않도록 처리
                    log.warn("[Scheduler] IPO 동기화 실패: corpCode={}", corpCode, e);
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] DART IPO 데이터 동기화 실패", e);
        }

        log.info("[Scheduler] DART IPO 데이터 동기화 종료");
    }

    // 모든 IpoEvent의 status를 오늘 날짜 기준으로 다시 계산해 DB에 반영하는 스케줄러 메서드
    // 매일 자정 30분에 실행되어 시간 경과로 인한 UPCOMING → ONGOING → CLOSED → LISTED 전이를 DB에 반영
    // 응답은 resolveStatus(today)로 항상 최신이지만, status 컬럼 필터 쿼리(findByStatus 등)가
    // stale 결과를 안 내도록 일일 동기화가 필요함
    @Scheduled(cron = "0 30 0 * * *")
    public void refreshIpoStatuses() {
        log.info("[Scheduler] IPO 상태 자동 갱신 시작");
        try {
            int updated = dartIpoSyncService.refreshAllIpoStatuses();
            log.info("[Scheduler] IPO 상태 자동 갱신 완료: {} 건 변경", updated);
        } catch (Exception e) {
            log.error("[Scheduler] IPO 상태 자동 갱신 실패", e);
        }
    }

    // 아직 원문이 파싱되지 않은 DART 공시를 찾아 ZIP 다운로드/파싱을 수행하는 스케줄러 메서드
    // 새벽 2시(DART 동기화 완료 후) + 오전 8시(당일 업무 전 보완 처리) 하루 2회 실행
    @Scheduled(cron = "0 0 2,8 * * *")
    public void parseUnparsedDartDisclosureDocuments() {
        if (!aiPipelinesEnabled) {
            log.info("[Scheduler] AI 파이프라인 비활성(scheduler.ai-pipelines.enabled=false) → parse-unparsed skip");
            return;
        }
        log.info("[Scheduler] DART 공시 원문 ZIP 파싱 시작");
        try {
            dartDisclosureParsingService.parseUnparsedDisclosureReports(50);
        } catch (Exception e) {
            log.error("[Scheduler] DART 공시 원문 ZIP 파싱 실패", e);
        }

        log.info("[Scheduler] DART 공시 원문 ZIP 파싱 종료");
    }

    // 활성 IPO(LISTED + 확정 listingDate가 아닌 모든 IPO)의 공시를 매일 일괄 재파싱
    // 컨텍스트 추출 로직이나 AI 프롬프트가 개선되면 신규 공시가 적은 환경에서도
    // 기존 데이터에 자동으로 적용되도록 함 (개발자 수동 호출 불필요)
    // 잘못된 stale 추정값 때문에 LISTED로 잘못 마킹된 IPO도 자동 복구 가능
    // 새벽 3시 (parse-unparsed 2시 완료 후) 실행
    @Scheduled(cron = "0 0 3 * * *")
    public void reparseActiveIpos() {
        if (!aiPipelinesEnabled) {
            log.info("[Scheduler] AI 파이프라인 비활성(scheduler.ai-pipelines.enabled=false) → reparse-active skip");
            return;
        }
        log.info("[Scheduler] 활성 IPO 일괄 재파싱 시작");
        try {
            dartDisclosureParsingService.reparseAllActiveIpos();
        } catch (Exception e) {
            log.error("[Scheduler] 활성 IPO 일괄 재파싱 실패", e);
        }
        log.info("[Scheduler] 활성 IPO 일괄 재파싱 종료");
    }

    // 홈/상세 화면 노출 범위의 IPO 기업들에 대해 최근 3개년 사업보고서 재무 하이라이트를 동기화
    // IPO/공시 파싱 배치 이후 실행해 신규 발견 기업도 함께 보강
    @Scheduled(cron = "0 0 4 * * *")
    public void syncActiveIpoFinancialHighlights() {
        log.info("[Scheduler] 활성 IPO 재무 하이라이트 동기화 시작");
        try {
            DartFinancialSyncService.FinancialHighlightSyncResult result =
                    dartFinancialSyncService.syncActiveIpoAnnualFinancialHighlights(3, null);

            log.info(
                    "[Scheduler] 활성 IPO 재무 하이라이트 동기화 완료: 대상 기업 {}개, 성공 {}건, 실패 {}건",
                    result.getTargetCompanyCount(),
                    result.getSuccessCount(),
                    result.getFailedCount()
            );
        } catch (Exception e) {
            log.error("[Scheduler] 활성 IPO 재무 하이라이트 동기화 실패", e);
        }
        log.info("[Scheduler] 활성 IPO 재무 하이라이트 동기화 종료");
    }


    // 상장 기업들의 KIS 주가 데이터를 동기화하는 스케줄러 메서드
    // 주식 장 마감 후 데이터를 가져오기 위해 오후 6시로 설정
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void syncListedCompanyPrices() {
        log.info("[Scheduler] KIS 상장 기업 주가 동기화 시작"); // 배치 시작 로그

        List<Company> companies = companyRepository.findByStockCodeIsNotNull();
        LocalDate today = LocalDate.now();

        String endDate = today.format(BASIC_DATE); // 오늘 날짜를 yyyyMMdd 문자열로 변환
        String startDate = today.minusDays(7).format(BASIC_DATE); // 오늘로부터 7일 전 날짜 변환 -> 최근 7일치 일봉 데이터 보강

        for (Company company : companies) {
            try { // 기업별 try
                kisStockPriceSyncService.syncCurrentPrice(company.getId()); // 해당 기업의 현재가 동기화
                kisStockPriceSyncService.syncDailyPrices(company.getId(), startDate, endDate); // 해당 기업의 최근 7일 일봉 데이터 동기화
            } catch (Exception e) { // 특정 기업의 주가 동기화에 실패했을 경우
                // 어떤 기업에서 실패했는지 companyId와 stockCode를 로그로 남김
                log.warn(
                        "[Scheduler] 주가 동기화 실패: companyId={}, stockCode={}",
                        company.getId(),
                        company.getStockCode(),
                        e // stack trace 출력
                );
            }
        }

        log.info("[Scheduler] KIS 상장 기업 주가 동기화 종료"); // 배치 종료 로그
    }
}
