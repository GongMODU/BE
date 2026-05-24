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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
// parseDisclosureReport()를 별도 컴포넌트로 분리하여 Spring AOP 프록시를 통한 @Transactional이 정상 동작하도록 함
// DartDisclosureParsingService에서 self-invocation으로 호출하면 트랜잭션이 무효화되는 문제를 해결
public class DisclosureParsingExecutor {

    private final DartApiClient dartApiClient;
    private final DisclosureTextExtractor disclosureTextExtractor;
    private final IpoEventRepository ipoEventRepository;
    private final IpoOfferingRepository ipoOfferingRepository;
    private final IpoMetricRepository ipoMetricRepository;
    private final IpoDisclosureReportRepository disclosureReportRepository;
    private final IpoDisclosureDocumentClassifier ipoDisclosureDocumentClassifier;
    private final DisclosureContextExtractor disclosureContextExtractor;
    private final AiDisclosureParsingService aiDisclosureParsingService;
    private final DisclosureParsingResultMerger disclosureParsingResultMerger;
    private final IpoBrokerWriter ipoBrokerWriter;

    @Transactional
    public void parseDisclosureReport(Long ipoEventId, String rceptNo) {
        IpoEvent ipoEvent = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        byte[] zipBytes = dartApiClient.downloadDisclosureDocumentZip(rceptNo);
        String originalText = disclosureTextExtractor.extractTextFromZip(zipBytes);

        if (originalText == null || originalText.isBlank()) {
            throw new CustomException(ErrorCode.DISCLOSURE_PARSING_FAILED);
        }

        DisclosureDocumentType documentType =
                ipoDisclosureDocumentClassifier.detectDocumentType(originalText);

        upsertDisclosureOriginalText(ipoEvent, rceptNo, originalText, documentType);

        if (documentType == DisclosureDocumentType.NON_IPO_DOCUMENT) {
            log.info(
                    "[DART Disclosure Parsing] IPO 관련 공시가 아니므로 값 파싱 건너뜀: rceptNo={}, documentType={}",
                    rceptNo,
                    documentType
            );
            return;
        }

        String aiContext = disclosureContextExtractor.extractContext(originalText);

        AiDisclosureParsingResult aiResult = null;
        if (aiContext != null && !aiContext.isBlank()) {
            aiResult = aiDisclosureParsingService.parseWithAi(aiContext);
        }

        IpoDisclosureParsingResult parsingResult =
                disclosureParsingResultMerger.toResult(aiResult);

        applyParsingResult(ipoEvent, parsingResult);
    }

    private IpoDisclosureReport upsertDisclosureOriginalText(
            IpoEvent ipoEvent,
            String rceptNo,
            String originalText,
            DisclosureDocumentType documentType
    ) {
        return disclosureReportRepository.findByIpoEventIdAndRceptNo(ipoEvent.getId(), rceptNo)
                .map(existing -> {
                    existing.updateOriginalText(originalText);
                    existing.updateDocumentType(documentType);
                    return existing;
                })
                .orElseGet(() -> disclosureReportRepository.save(
                        IpoDisclosureReport.builder()
                                .ipoEvent(ipoEvent)
                                .rceptNo(rceptNo)
                                .reportName("DART 원문 공시")
                                .documentType(documentType)
                                .originalText(originalText)
                                .build()
                ));
    }

    // 청약 종료 후 한국 IPO 상장까지 통상 소요 일수 (거래소 상장승인 절차 포함)
    // 발행조건확정 공시에 실제 상장일이 안 적혀있는 케이스가 많아 추정값으로 채워두고,
    // 추후 증권발행실적보고서에서 실제 상장일이 파싱되면 자동 덮어쓰기
    private static final int LISTING_DATE_OFFSET_DAYS_FROM_SUBSCRIPTION_END = 7;

    private void applyParsingResult(IpoEvent ipoEvent, IpoDisclosureParsingResult result) {
        // 1) listingDate: AI 추출값 우선
        //    - 실제 추출 → estimated=false (확정값으로 덮어쓰기)
        //    - AI는 null인데 기존 값이 없거나 기존 값도 추정값이면 → 새 데이터로 다시 추정
        //      (subscriptionEndDate가 정정되면 listingDate 추정값도 함께 갱신됨)
        //    - AI는 null이고 기존이 확정값이면 → 그대로 유지
        LocalDate effectiveListingDate = result.getListingDate();
        Boolean listingDateEstimatedFlag = null;
        if (effectiveListingDate != null) {
            listingDateEstimatedFlag = false;
        } else if (ipoEvent.getListingDate() == null
                || Boolean.TRUE.equals(ipoEvent.getListingDateEstimated())) {
            LocalDate baseSubEnd = result.getSubscriptionEnd() != null
                    ? result.getSubscriptionEnd()
                    : ipoEvent.getSubscriptionEndDate();
            if (baseSubEnd != null) {
                effectiveListingDate = baseSubEnd.plusDays(LISTING_DATE_OFFSET_DAYS_FROM_SUBSCRIPTION_END);
                listingDateEstimatedFlag = true;
            }
        }

        // 2) lockupExpiryDate: AI 직접 추출값 우선
        //    - AI 직접 추출 → estimated=false
        //    - AI는 null인데 lockupPeriodMonths가 있고 기존이 없거나 추정값이면 → 다시 계산
        //    - listingDate가 갱신됐어도 lockupPeriodMonths가 이번 result에 없으면 lockup은 그대로 (다음 파싱 때 자연스럽게 수렴)
        LocalDate lockupExpiryDate = result.getLockupExpiryDate();
        Boolean lockupExpiryEstimatedFlag = null;
        if (lockupExpiryDate != null) {
            lockupExpiryEstimatedFlag = false;
        } else if (result.getLockupPeriodMonths() != null
                && (ipoEvent.getLockupExpiryDate() == null
                        || Boolean.TRUE.equals(ipoEvent.getLockupExpiryDateEstimated()))) {
            LocalDate baseListingDate = effectiveListingDate != null
                    ? effectiveListingDate
                    : ipoEvent.getListingDate();
            if (baseListingDate != null) {
                lockupExpiryDate = baseListingDate.plusMonths(result.getLockupPeriodMonths());
                lockupExpiryEstimatedFlag = true;
            }
        }

        ipoEvent.updateParsedSchedule(
                result.getDemandForecastStart(),
                result.getDemandForecastEnd(),
                result.getSubscriptionStart(),
                result.getSubscriptionEnd(),
                result.getRefundDate(),
                effectiveListingDate,
                lockupExpiryDate
        );

        // 값이 갱신된 경우에만 추정 플래그도 함께 변경
        ipoEvent.markListingDateEstimated(listingDateEstimatedFlag);
        ipoEvent.markLockupExpiryDateEstimated(lockupExpiryEstimatedFlag);

        ipoEvent.updateStatus(ipoEvent.resolveStatus(LocalDate.now()));

        IpoOffering offering = ipoOfferingRepository.findByIpoEventId(ipoEvent.getId())
                .orElseGet(() -> ipoOfferingRepository.save(
                        IpoOffering.builder()
                                .ipoEvent(ipoEvent)
                                .build()
                ));

        offering.updateParsedOfferingInfo(
                result.getShareCount(),
                result.getTotalListedShares(),
                result.getOfferPriceMin(),
                result.getOfferPriceMax(),
                result.getOfferPrice()
        );

        IpoMetric metric = ipoMetricRepository.findByIpoEventId(ipoEvent.getId())
                .orElseGet(() -> ipoMetricRepository.save(
                        IpoMetric.builder()
                                .ipoEvent(ipoEvent)
                                .build()
                ));

        metric.updateParsedMetrics(
                result.getInstitutionalCompetitionRate(),
                result.getLockupRatio(),
                result.getProtectiveCustodyRatio()
        );

        ipoBrokerWriter.upsertBrokers(ipoEvent, result.getBrokerNames());
    }
}
