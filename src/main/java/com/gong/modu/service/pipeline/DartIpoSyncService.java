package com.gong.modu.service.pipeline;

import com.gong.modu.client.DartApiClient;
import com.gong.modu.domain.dto.dart.DartDisclosureSearchResponse;
import com.gong.modu.domain.dto.dart.DartEquitySecuritiesReportResponse;
import com.gong.modu.domain.entity.ipo.*;
import com.gong.modu.domain.enums.ipo.BrokerRole;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.IpoEventType;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.*;
import com.gong.modu.util.ExternalDateParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
// 공모주 관련 DART 데이터를 DB에 저장하는 Service
// DART 공시검색 API와 지분증권 주요신고서 주요정보 API를 이용하여 ipo_events, ipo_offerings, brokers, ipo_event_brokers, ipo_disclosure_reports를 채우는 서비스
public class DartIpoSyncService {

    // DART 공시 유형 코드 - C: 발행공시 (증권신고서, 투자설명서, 증권발행실적보고서 등)
    private static final String ISSUANCE_DISCLOSURE_TYPE = "C";

    // DART 법인구분 코드 - E: 기타법인 (상장 전 기업 = 신규 IPO 후보)
    // 유상증자·합병 공시를 내는 기존 상장사(Y/K/N)를 발견 단계에서 제외하기 위함
    private static final String NON_LISTED_CORP_CLASS = "E";

    // 시장 전체 공시 검색 시 페이지네이션 안전 상한
    private static final int MAX_SEARCH_PAGES = 50;

    // DART 시장 전체 공시검색은 조회 기간을 약 3개월로 제한하므로, 한 번에 검색할 최대 일수
    private static final int MAX_SEARCH_RANGE_DAYS = 80;

    // 기업별 동기화 시 과거로 거슬러 조회할 개월 수
    // estkRs는 '최초접수일' 기준으로 필터링되는데, 한 IPO는 최초 증권신고서~상장까지 수개월이 걸리므로
    // 발견 윈도우보다 더 과거까지 봐야 최초 증권신고서를 놓치지 않는다
    private static final int PER_COMPANY_LOOKBACK_MONTHS = 12;

    // yyyyMMdd 형식 날짜 포매터
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DartApiClient dartApiClient;
    private final CompanyRepository companyRepository;
    private final IpoEventRepository ipoEventRepository;
    private final IpoOfferingRepository ipoOfferingRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;
    private final BrokerRepository brokerRepository;
    private final IpoDisclosureReportRepository disclosureReportRepository;
    private final IpoDisclosureDocumentClassifier ipoDisclosureDocumentClassifier;

    // 모든 IpoEvent의 status를 오늘 날짜 기준으로 다시 계산해 DB에 반영하는 메서드
    // 일정 정보(청약·상장일)는 그대로인데 시간이 흘러 상태가 바뀌어야 하는 경우(UPCOMING → ONGOING → CLOSED → LISTED) 처리
    // status 컬럼으로 필터하는 쿼리(findByStatus 등)가 stale 상태로 잘못된 결과 내는 걸 방지하기 위해 매일 자정 직후 일괄 호출
    // company/offering/metric 같은 lazy 연관관계는 건드리지 않으므로 LazyInit 안전
    @Transactional
    public int refreshAllIpoStatuses() {
        LocalDate today = LocalDate.now();
        List<IpoEvent> allEvents = ipoEventRepository.findAll();
        int updatedCount = 0;
        for (IpoEvent event : allEvents) {
            IpoEventStatus next = event.resolveStatus(today);
            if (next != event.getStatus()) {
                event.updateStatus(next);
                updatedCount++;
            }
        }
        return updatedCount;
    }

    @Transactional
    // 특정 기업의 특정 기간 공모 관련 데이터를 동기화하는 메서드
    public void syncIpoByCompany(String corpCode, String beginDate, String endDate){
        Company company = companyRepository.findByCorpCode(corpCode) // corpCode로 기업을 조회
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        // 기업별 조회는 최초 증권신고서를 놓치지 않도록 시작일을 과거로 넓힘
        // (estkRs/공시검색이 최초접수일 기준으로 필터링되기 때문)
        String wideBeginDate = widenBeginDate(beginDate, endDate);

        // DART 공시검색 API 호출
        // disclosureType -> null : 초기에는 전체 공시에서 reportNm 기준으로 증권신고서를 필터링하기 위함
        DartDisclosureSearchResponse disclosureSearchResponse = dartApiClient.searchDisclosure(
                wideBeginDate,
                endDate,
                corpCode,
                null,
                1,
                100
        );

        if (disclosureSearchResponse.getList() != null) {
            disclosureSearchResponse.getList().stream()
                    // 증권신고서·투자설명서·증권발행실적보고서·신규상장 등 공모주 관련 공시만 필터링
                    .filter(item -> ipoDisclosureDocumentClassifier.isIpoRelevantReportName(item.getReportNm()))
                    .forEach(item -> upsertDisclosureReport(company, item)); // 각 공시 항목을 ipo_disclosure_reports에 저장하거나 갱신
        }

        // DART 지분증권 증권신고서 주요정보 API 호출: 청약기일, 납입기일, 인수기관명 등의 구조화 데이터를 얻기 위해 사용
        DartEquitySecuritiesReportResponse equityResponse =
                dartApiClient.getEquitySecuritiesReport(corpCode, wideBeginDate, endDate);

        if (equityResponse.getList() == null) {
            return; // 주요정보 목록이 없다면 더 처리할 데이터가 없으므로 메서드 종료
        }

        // 주요 정보 목록을 하나씩 순회
        for (DartEquitySecuritiesReportResponse.Item item : equityResponse.getList()) {
            upsertIpoCoreData(company, item); // 각 주요정보 항목을 바탕으로 ipo_events, ipo_offerings, broker 관련 데이터를 저장/갱신
        }
    }

    // 기업별 조회 시작일을 PER_COMPANY_LOOKBACK_MONTHS만큼 과거로 넓히는 메서드
    // 호출자가 더 넓은 범위를 줬다면 그 값을 그대로 사용
    private String widenBeginDate(String beginDate, String endDate) {
        LocalDate passedBegin = LocalDate.parse(beginDate, BASIC_DATE);
        LocalDate widened = LocalDate.parse(endDate, BASIC_DATE).minusMonths(PER_COMPANY_LOOKBACK_MONTHS);
        LocalDate effective = widened.isBefore(passedBegin) ? widened : passedBegin;
        return effective.format(BASIC_DATE);
    }

    // 시장 전체 발행공시를 검색해 공모주 관련 공시를 제출한 기업 고유번호(corpCode)를 수집하는 메서드
    // 스케줄러가 어떤 기업을 동기화해야 하는지 찾는 진입점으로 사용 (DB를 건드리지 않는 조회 전용)
    // DART 시장 전체 검색은 조회 기간이 약 3개월로 제한되므로 기간을 분할해 검색한다
    public Set<String> findRecentIpoCorpCodes(String beginDate, String endDate) {
        Set<String> corpCodes = new LinkedHashSet<>();

        LocalDate begin = LocalDate.parse(beginDate, BASIC_DATE);
        LocalDate end = LocalDate.parse(endDate, BASIC_DATE);

        // [begin, end]를 MAX_SEARCH_RANGE_DAYS 이하 구간으로 나눠 각 구간을 검색
        LocalDate chunkStart = begin;
        while (!chunkStart.isAfter(end)) {
            LocalDate chunkEnd = chunkStart.plusDays(MAX_SEARCH_RANGE_DAYS);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }

            collectCorpCodesInRange(
                    chunkStart.format(BASIC_DATE),
                    chunkEnd.format(BASIC_DATE),
                    corpCodes
            );

            chunkStart = chunkEnd.plusDays(1);
        }

        return corpCodes;
    }

    // 특정 기간의 시장 전체 발행공시를 페이지네이션하며 공모주 후보 기업 고유번호를 수집하는 메서드
    private void collectCorpCodesInRange(String beginDate, String endDate, Set<String> corpCodes) {
        int pageNo = 1;

        while (pageNo <= MAX_SEARCH_PAGES) {
            // corpCode를 null로 두면 시장 전체 공시를 검색
            DartDisclosureSearchResponse response = dartApiClient.searchDisclosure(
                    beginDate, endDate, null, ISSUANCE_DISCLOSURE_TYPE, pageNo, 100
            );

            if (response.getList() == null || response.getList().isEmpty()) {
                break;
            }

            // 지분증권 증권신고서 + 상장 전 기타법인(E)만 골라 기업 고유번호 수집
            // - 지분증권 필터: 채무증권(회사채) 등 비지분증권 증권신고서를 제외
            // - corp_cls 필터: 기존 상장사의 유상증자·합병을 제외해 신규 IPO만 추림
            response.getList().stream()
                    .filter(item -> ipoDisclosureDocumentClassifier.isEquityOfferingReportName(item.getReportNm()))
                    .filter(item -> NON_LISTED_CORP_CLASS.equals(item.getCorpCls()))
                    .map(DartDisclosureSearchResponse.Item::getCorpCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(corpCodes::add);

            // 마지막 페이지까지 순회했으면 종료
            if (response.getTotalPage() == null || pageNo >= response.getTotalPage()) {
                break;
            }

            pageNo++;
        }
    }

    // 공시 검색 결과 1건을 ipo_disclosure_reports에 저장하거나 갱신하는 메서드
    private void upsertDisclosureReport(
            Company company,
            DartDisclosureSearchResponse.Item item
    ) {
        // 공시 접수번호를 기준으로 IpoEvent를 찾거나 새로 생성
        // 공시 보고서를 특정 공모 이벤트에 연결되어야 하므로 먼저 IpoEvent가 필요함
        IpoEvent ipoEvent = findOrCreateIpoEvent(
                company, item.getRceptNo(), item.getReportNm()
        );

        // reportName만 보고 문서 성격을 1차 판별
        DisclosureDocumentType documentType =
                ipoDisclosureDocumentClassifier.detectDocumentTypeFromReportName(item.getReportNm());

        disclosureReportRepository.findByIpoEventIdAndRceptNo(ipoEvent.getId(), item.getRceptNo())
                .ifPresentOrElse(
                        // 이미 존재하면 reportName만 최신값으로 갱신
                        existing -> existing.updateReportInfo(item.getReportNm(), documentType),

                        () -> disclosureReportRepository.save( // 없으면 새 IpoDisclosureReport 저장
                                IpoDisclosureReport.builder()
                                        .ipoEvent(ipoEvent)
                                        .rceptNo(item.getRceptNo())
                                        .reportName(item.getReportNm())
                                        .documentType(documentType)
                                        .build()
                        )
                );
    }

    // 지분증권 증권신고서 주요정보 응답 1건을 IPO 핵심 데이터로 저장/갱신하는 메서드
    private void upsertIpoCoreData(
            Company company,
            DartEquitySecuritiesReportResponse.Item item
    ) {
        // 접수번호 기준으로 IpoEvent를 찾거나 생성
        IpoEvent ipoEvent = findOrCreateIpoEvent(company, item.getRceptNo(), item.getCorpName());

        // 청약기일 문자열에서 첫 번째 날짜를 시작일로 추출합
        LocalDate subscriptionStart = ExternalDateParser.parseFirstDateFromRange(item.getSbd());

        // 청약기일 문자열에서 마지막 날짜를 종료일로 추출
        LocalDate subscriptionEnd = ExternalDateParser.parseLastDateFromRange(item.getSbd());

        // 납입기일 문자열을 LocalDate로 변환
        LocalDate paymentDate = ExternalDateParser.parseFlexibleDate(item.getPymd());

        // 배정공고일 문자열을 LocalDate로 변환
        LocalDate allocationDate = ExternalDateParser.parseFlexibleDate(item.getAsand());

        // 공모 이벤트 일정 정보 갱신
        // DART 주요정보 응답에서 수요예측일, 환불일, 상장일, 보호예수 해제일은 직접 얻지 못하므로 null로 처리 -> 해당 값은 원문 파싱이나 관리자 입력 필요
        ipoEvent.updateDartSchedule(
                subscriptionStart,
                subscriptionEnd,
                paymentDate,
                allocationDate
        );

        // 청약/상장 일정 기준으로 공모 상태를 계산하여 갱신
        ipoEvent.updateStatus(ipoEvent.resolveStatus(LocalDate.now()));

        // 청약공고일, 배정기준일 등 공모 조건 보조 정보를 저장/갱신
        upsertOffering(ipoEvent, item);

        // 인수기관 문자열을 바탕으로 증권사 정보를 저장/갱신
        upsertBroker(ipoEvent, item.getUdtintnm());
    }

    // 기업을 기준으로 IpoEvent를 찾거나 새로 생성하는 메서드
    // 한 기업의 IPO는 최초·정정 증권신고서, 투자설명서, 발행실적 등 여러 공시로 구성되므로
    // 공시 접수번호(rceptNo) 단위가 아니라 기업 단위로 하나의 IpoEvent를 공유한다
    private IpoEvent findOrCreateIpoEvent(
            Company company,
            String rceptNo,
            String eventName
    ) {
        return ipoEventRepository.findByCompanyId(company.getId()).stream()
                .findFirst() // 해당 기업의 기존 공모 이벤트가 있으면 재사용
                .orElseGet(() -> ipoEventRepository.save(
                        IpoEvent.builder()
                                .company(company)
                                .rceptNo(rceptNo)
                                .mainReportRceptNo(rceptNo)
                                .eventName(eventName)
                                .eventType(IpoEventType.IPO) // 공모 이벤트 유형을 일반 IPO로 기본 설정
                                .marketType(company.getMarketType())
                                .status(IpoEventStatus.UPCOMING) // 새로 만든 이벤트는 예정 상태로 기본 설정
                                .build()
                ));
    }

    // 지분증권 주요정보에서 얻은 일정성 공모 조건을 ipo_offerings에 저장/갱신하는 메서드
    private void upsertOffering(
            IpoEvent ipoEvent,
            DartEquitySecuritiesReportResponse.Item item
    ) {
        LocalDate subscriptionNoticeDate = ExternalDateParser.parseFlexibleDate(item.getSband()); // 청약공고일 변환
        LocalDate allocationBaseDate = ExternalDateParser.parseFlexibleDate(item.getAsstd()); // 배정기준일 변환
        ipoOfferingRepository.findByIpoEventId(ipoEvent.getId())
                .ifPresentOrElse(
                        // 기존 레코드가 있으면 날짜 정보만 갱신
                        existing -> existing.updateOfferingDates(subscriptionNoticeDate, allocationBaseDate),
                        () -> ipoOfferingRepository.save(
                                IpoOffering.builder()
                                        .ipoEvent(ipoEvent)
                                        .subscriptionNoticeDate(subscriptionNoticeDate)
                                        .allocationBaseDate(allocationBaseDate)
                                        .build()
                        )
                );
    }

    // 인수기관명 문자열을 Broker와 IpoEventBroker로 저장하는 메서드
    private void upsertBroker(IpoEvent ipoEvent, String brokerNamesText) {
        if (brokerNamesText == null || brokerNamesText.isBlank()) {
            return; // 인수기관명이 없으면 저장할 증권사 정보가 없음
        }

        String[] brokerNames = brokerNamesText.split("[,/]");

        for (String rawName : brokerNames) {
            String brokerName = rawName.trim();

            if (brokerName.isBlank()) {
                continue;
            }

            Broker broker = brokerRepository.findByName(brokerName)
                    .orElseGet(() -> brokerRepository.save(
                            Broker.builder()
                                    .name(brokerName)
                                    .build()
                    ));

            boolean alreadyExists = ipoEventBrokerRepository
                    .findByIpoEventId(ipoEvent.getId()) // 해당 공모 이벤트에 이미 연결된 증권사 목록을 가져옴
                    .stream()
                    .anyMatch(eventBroker -> brokerName.equals(eventBroker.getBrokerName()));

            if (!alreadyExists) {
                ipoEventBrokerRepository.save(
                        IpoEventBroker.builder()
                                .ipoEvent(ipoEvent)
                                .broker(broker) // 정규화된 증권사 마스터
                                .brokerName(brokerName) // 원문 또는 정제된 증권사명
                                .role(BrokerRole.UNDERWRITER) // 초기 기본 역할은 인수회사로 설정
                                .build()
                );
            }
        }
    }

}
