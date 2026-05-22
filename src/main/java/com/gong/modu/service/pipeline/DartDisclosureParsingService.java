package com.gong.modu.service.pipeline;

import com.gong.modu.client.DartApiClient;
import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.domain.dto.pipeline.IpoDisclosureParsingResult;
import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.entity.ipo.IpoMetric;
import com.gong.modu.domain.entity.ipo.IpoOffering;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoDisclosureReportRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.repository.ipo.IpoMetricRepository;
import com.gong.modu.repository.ipo.IpoOfferingRepository;
import com.gong.modu.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
// DART 공시 원문 ZIP을 다운로드하고 AI 파싱을 수행한 뒤, 일부 IPO 핵심값을 파싱하여 DB에 반영하는 서비스 클래스
public class DartDisclosureParsingService {

    private final DartApiClient dartApiClient;
    private final DisclosureTextExtractor disclosureTextExtractor;
    private final IpoEventRepository ipoEventRepository;
    private final IpoOfferingRepository ipoOfferingRepository;
    private final IpoMetricRepository ipoMetricRepository;
    private final IpoDisclosureReportRepository disclosureReportRepository;
    private final RedisUtil redisUtil;
    private final IpoDisclosureDocumentClassifier ipoDisclosureDocumentClassifier;

    // AI 파싱에 필요한 문맥을 추출하는 클래스
    private final DisclosureContextExtractor disclosureContextExtractor;
    // 클로드를 호출해서 IPO 핵심값을 추출하는 서비스
    private final AiDisclosureParsingService aiDisclosureParsingService;
    // AI 파싱 결과를 검증·변환하는 클래스
    private final DisclosureParsingResultMerger disclosureParsingResultMerger;

    // 아직 original_text가 없는 공시들을 일정 개수만큼 찾아 원문 ZIP 다운로드/파싱을 수행하는 메서드
    public void parseUnparsedDisclosureReports(int limit) {
        // 공시 조회
        List<IpoDisclosureReport> reports = disclosureReportRepository
                .findByOriginalTextIsNull(PageRequest.of(0, limit));

        if (reports.isEmpty()) {
            log.info("DART Disclosure Parsing] 미파싱 공시 없음");
            return;
        }

        for (IpoDisclosureReport report : reports) {

            String rceptNo = report.getRceptNo();

            // 최근 실패한 공시는 일정 시간 동안 재시도 X
            if (redisUtil.isDisclosureParsingRecentlyFailed(rceptNo)) {
                log.info("[DART Disclosure Parsing] 최근 실패 이력으로 건너뜀: rceptNo={}", rceptNo);
                continue;
            }

            // 같은 공시를 여러 스케줄러 인스턴스가 동시에 처리하지 않도록 Redis lock을 획득
            boolean locked = redisUtil.tryLockDisclosureParsing(rceptNo, 30);

            if (!locked) {
                log.info("[DART Disclosure Parsing] 이미 파싱 중인 공시로 건너뜀: rceptNo={}", rceptNo);
                continue;
            }

            try {
                // 각 공시마다 단건 파싱 메서드 호출
                parseDisclosureReport(report.getIpoEvent().getId(), rceptNo);
            } catch (Exception e) {
                // 실패한 공시는 6시간 동안 재시도를 막음
                redisUtil.markDisclosureParsingFailed(rceptNo, 6);

                log.warn("[DART Disclosure Parsing] 공시 원문 파싱 실패: rceptNo={}", rceptNo, e);
            } finally {
                // 성공/실패 여부와 관계없이 lock은 해제
                redisUtil.unlockDisclosureParsing(rceptNo);
            }
        }

    }

    // DART 공시 접수번호를 기준으로 ZIP 다운로드, 텍스트 추출, 정규식 파싱, AI 보완 파싱, DB 반영까지 수행하는 메서드
    @Transactional
    public void parseDisclosureReport(Long ipoEventId, String rceptNo) {
        IpoEvent ipoEvent = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        // DART 공시 원문 ZIP 파일 다운로드
        byte[] zipBytes = dartApiClient.downloadDisclosureDocumentZip(rceptNo);

        // 텍스트 추출
        String originalText = disclosureTextExtractor.extractTextFromZip(zipBytes);

        if (originalText == null || originalText.isBlank()) {
            throw new CustomException(ErrorCode.DISCLOSURE_PARSING_FAILED);
        }

        // 원문 텍스트 기준으로 문서 성격을 다시 판별
        DisclosureDocumentType documentType =
                ipoDisclosureDocumentClassifier.detectDocumentType(originalText);

        // 원문 텍스트와 documentType을 ipo_disclosure_reports에 저장하거나 갱신
        upsertDisclosureOriginalText(ipoEvent, rceptNo, originalText, documentType);

        // IPO 관련 문서가 아니라면 파싱 X
        if (documentType == DisclosureDocumentType.NON_IPO_DOCUMENT) {
            log.info(
                    "[DART Disclosure Parsing] IPO 관련 공시가 아니므로 값 파싱 건너뜀: rceptNo={}, documentType={}",
                    rceptNo,
                    documentType
            );
            return;
        }

        // 원문에서 AI 파싱에 필요한 섹션 문맥을 추출
        String aiContext = disclosureContextExtractor.extractContext(originalText);

        // AI 파싱 결과를 담을 변수
        AiDisclosureParsingResult aiResult = null;

        if (aiContext != null && !aiContext.isBlank()) {
            aiResult = aiDisclosureParsingService.parseWithAi(aiContext);
        }

        // AI 결과를 검증·변환하여 최종 파싱 결과 생성
        IpoDisclosureParsingResult parsingResult =
                disclosureParsingResultMerger.toResult(aiResult);

        // 파싱 결과를 ipo_events, ipo_offerings, ipo_metrics에 반영
        applyParsingResult(ipoEvent, parsingResult);

    }

    // 공시 원문 텍스트를 ipo_disclosure_reports에에 저장하거나 갱신하는 메서드
    private IpoDisclosureReport upsertDisclosureOriginalText(
            IpoEvent ipoEvent,
            String rceptNo,
            String originalText,
            DisclosureDocumentType documentType
    ) {
        return disclosureReportRepository.findByIpoEventIdAndRceptNo(ipoEvent.getId(), rceptNo)
                .map(existing -> {
                    // 기존 공시 레코드의 원문 텍스트를 최신 추출 결과로 갱신
                    existing.updateOriginalText(originalText);

                    // 원문 텍스트 기준으로 다시 판별한 문서 성격을 갱신
                    existing.updateDocumentType(documentType);

                    return existing;
                })
                .orElseGet(() -> disclosureReportRepository.save(
                        IpoDisclosureReport.builder()
                                .ipoEvent(ipoEvent)
                                .rceptNo(rceptNo)
                                .reportName("DART 원문 공시") // 임시명
                                .documentType(documentType)
                                .originalText(originalText)
                                .build()
                ));
    }

    // 파싱 결과를 각 엔티티에 반영하는 메서드
    private void applyParsingResult(
            IpoEvent ipoEvent,
            IpoDisclosureParsingResult result
    ) {
        // 공시 원문에서 추출한 일정 정보를 ipo_events에 반영
        ipoEvent.updateParsedSchedule(
                result.getDemandForecastStart(),
                result.getDemandForecastEnd(),
                result.getRefundDate(),
                result.getListingDate(),
                result.getLockupExpiryDate()
        );

        // 공모조건 조회
        IpoOffering offering = ipoOfferingRepository.findByIpoEventId(ipoEvent.getId())
                .orElseGet(() -> ipoOfferingRepository.save(
                        IpoOffering.builder()
                                .ipoEvent(ipoEvent)
                                .build()
                ));

        // 파싱된 공모가, 공모주식수, 상장주식수를 공모 조건에 반영
        offering.updateParsedOfferingInfo(
                result.getShareCount(),
                result.getTotalListedShares(),
                result.getOfferPriceMin(),
                result.getOfferPriceMax(),
                result.getOfferPrice()
        );

        // 공모지표 조회
        IpoMetric metric = ipoMetricRepository.findByIpoEventId(ipoEvent.getId())
                .orElseGet(() -> ipoMetricRepository.save(
                        IpoMetric.builder()
                                .ipoEvent(ipoEvent)
                                .build()
                ));

        // 파싱된 기관경쟁률, 의무보유확약, 락업 비율을 지표에 반영
        metric.updateParsedMetrics(
                result.getInstitutionalCompetitionRate(),
                result.getLockupRatio(),
                result.getProtectiveCustodyRatio()
        );
    }
}
