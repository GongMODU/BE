package com.gong.modu.scheduler;

import com.gong.modu.domain.entity.ipo.Company;
import com.gong.modu.repository.ipo.CompanyRepository;
import com.gong.modu.service.pipeline.DartCompanySyncService;
import com.gong.modu.service.pipeline.DartDisclosureParsingService;
import com.gong.modu.service.pipeline.DartIpoSyncService;
import com.gong.modu.service.pipeline.KisStockPriceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    // 매일 실행되므로 누락 방지를 위한 버퍼를 포함해 넉넉히 설정
    private static final int IPO_SYNC_LOOKBACK_DAYS = 35;

    private final CompanyRepository companyRepository;
    private final KisStockPriceSyncService kisStockPriceSyncService; // 실제 KIS 주가 동기화 로직을 실행할 서비스

    private final DartCompanySyncService dartCompanySyncService; // DART 기업개황을 companies 테이블에 동기화하는 서비스
    private final DartIpoSyncService dartIpoSyncService; // DART 공시/주요정보를 IPO 데이터로 동기화하는 서비스
    private final DartDisclosureParsingService dartDisclosureParsingService; // DART 공시 원문 다운/파싱 로직을 실행하는 서비스

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

    // 아직 원문이 파싱되지 않은 DART 공시를 찾아 ZIP 다운로드/파싱을 수행하는 스케줄러 메서드
    @Scheduled(cron = "0 0 2 * * *")
    public void parseUnparsedDartDisclosureDocuments() {
        log.info("[Scheduler] DART 공시 원문 ZIP 파싱 시작");
        try {
            dartDisclosureParsingService.parseUnparsedDisclosureReports(20);
        } catch (Exception e) {
            log.error("[Scheduler] DART 공시 원문 ZIP 파싱 실패", e);
        }

        log.info("[Scheduler] DART 공시 원문 ZIP 파싱 종료");
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
