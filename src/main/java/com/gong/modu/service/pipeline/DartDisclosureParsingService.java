package com.gong.modu.service.pipeline;

import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
// 미파싱 공시 목록을 조회하고 병렬로 파싱 작업을 분배하는 오케스트레이터
// 실제 파싱 트랜잭션은 DisclosureParsingExecutor에 위임하여 @Transactional이 정상 동작하게 함
public class DartDisclosureParsingService {

    private final IpoDisclosureReportRepository disclosureReportRepository;
    private final RedisUtil redisUtil;
    private final DisclosureParsingExecutor disclosureParsingExecutor;
    private final Executor disclosureParsingPool;

    public DartDisclosureParsingService(
            IpoDisclosureReportRepository disclosureReportRepository,
            RedisUtil redisUtil,
            DisclosureParsingExecutor disclosureParsingExecutor,
            @Qualifier("disclosureParsingPool") Executor disclosureParsingPool
    ) {
        this.disclosureReportRepository = disclosureReportRepository;
        this.redisUtil = redisUtil;
        this.disclosureParsingExecutor = disclosureParsingExecutor;
        this.disclosureParsingPool = disclosureParsingPool;
    }

    // 아직 original_text가 없는 공시들을 일정 개수만큼 찾아 병렬로 파싱을 수행하는 메서드
    // 같은 IPO의 공시는 순차 처리(race condition 방지), 다른 IPO끼리만 병렬
    public void parseUnparsedDisclosureReports(int limit) {
        List<IpoDisclosureReport> reports = disclosureReportRepository
                .findByOriginalTextIsNull(PageRequest.of(0, limit));

        if (reports.isEmpty()) {
            log.info("[DART Disclosure Parsing] 미파싱 공시 없음");
            return;
        }

        // 실패 이력 / lock 필터링 후 IPO별로 그룹핑
        Map<Long, List<IpoDisclosureReport>> grouped = reports.stream()
                .filter(report -> {
                    if (redisUtil.isDisclosureParsingRecentlyFailed(report.getRceptNo())) {
                        log.info("[DART Disclosure Parsing] 최근 실패 이력으로 건너뜀: rceptNo={}", report.getRceptNo());
                        return false;
                    }
                    return true;
                })
                .filter(report -> {
                    if (!redisUtil.tryLockDisclosureParsing(report.getRceptNo(), 30)) {
                        log.info("[DART Disclosure Parsing] 이미 파싱 중인 공시로 건너뜀: rceptNo={}", report.getRceptNo());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.groupingBy(r -> r.getIpoEvent().getId()));

        List<CompletableFuture<Void>> futures = grouped.values().stream()
                .map(reportsInOneIpo -> CompletableFuture.runAsync(() -> {
                    for (IpoDisclosureReport report : reportsInOneIpo) {
                        String rceptNo = report.getRceptNo();
                        try {
                            disclosureParsingExecutor.parseDisclosureReport(
                                    report.getIpoEvent().getId(), rceptNo);
                        } catch (Exception e) {
                            // DART에 ZIP 원문 자체가 없는 케이스(status 014)는 등록될 때까지 영구적 실패이므로 긴 TTL
                            // 그 외 일시적 오류(네트워크/일부 다운로드 실패 등)는 짧은 TTL로 재시도 허용
                            long failureTtlHours = isPermanentDartFailure(e) ? 24 * 7 : 6;
                            redisUtil.markDisclosureParsingFailed(rceptNo, failureTtlHours);
                            log.warn("[DART Disclosure Parsing] 공시 원문 파싱 실패 (재시도 TTL={}h): rceptNo={}",
                                    failureTtlHours, rceptNo, e);
                        } finally {
                            redisUtil.unlockDisclosureParsing(rceptNo);
                        }
                    }
                }, disclosureParsingPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    // DART에 ZIP이 영구적으로 없는 케이스인지 판별 (긴 TTL 적용 대상)
    private boolean isPermanentDartFailure(Throwable e) {
        return e instanceof CustomException ce
                && ce.getErrorCode() == ErrorCode.DART_DOCUMENT_NOT_AVAILABLE;
    }

    // 테스트·수동 재파싱용 단건 호출 (DisclosureParserTestController 등에서 사용)
    public void parseDisclosureReport(Long ipoEventId, String rceptNo) {
        disclosureParsingExecutor.parseDisclosureReport(ipoEventId, rceptNo);
    }

    // 재파싱이 필요한 모든 IPO의 공시를 일괄 재파싱하는 메서드
    // "재파싱이 필요한" 정의: status가 LISTED가 아니거나 listingDate가 추정값/NULL인 IPO
    //   → 진짜 완료된 IPO는 skip, 활성 IPO + 의심스러운 LISTED는 모두 포함
    // 잘못된 stale 추정값 때문에 LISTED로 잘못 마킹된 IPO도 다시 잡혀 정상 복원됨
    // 같은 IPO의 공시는 순차 처리(race condition 방지), 다른 IPO끼리만 병렬
    public void reparseAllActiveIpos() {
        List<Object[]> reports = disclosureReportRepository
                .findReportsForActiveIpos(IpoEventStatus.LISTED);

        if (reports.isEmpty()) {
            log.info("[DART Disclosure Parsing] 재파싱 대상 IPO 없음");
            return;
        }

        // IPO별로 그룹핑 (Long → List<String>)
        Map<Long, List<String>> reportsByIpo = reports.stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        log.info("[DART Disclosure Parsing] 활성 IPO 재파싱 시작: {} IPO, {} 건",
                reportsByIpo.size(), reports.size());

        List<CompletableFuture<Void>> futures = reportsByIpo.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    Long ipoEventId = entry.getKey();
                    for (String rceptNo : entry.getValue()) {
                        try {
                            disclosureParsingExecutor.parseDisclosureReport(ipoEventId, rceptNo);
                        } catch (Exception e) {
                            log.warn("[DART Disclosure Parsing] 재파싱 실패: rceptNo={}", rceptNo, e);
                        }
                    }
                }, disclosureParsingPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("[DART Disclosure Parsing] 활성 IPO 재파싱 완료: {} 건", reports.size());
    }
}
