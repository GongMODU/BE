package com.gong.modu.service.pipeline;

import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// DART 공시 원문 텍스트가 IPO 관련 문서인지, 어떤 성격의 문서인지 판별하는 클래스
@Component
public class IpoDisclosureDocumentClassifier {
    private static final List<String> IPO_KEYWORDS = List.of(
            "증권신고서",
            "지분증권",
            "투자설명서",
            "공모",
            "공모가",
            "희망공모가",
            "확정공모가",
            "청약",
            "수요예측",
            "기관투자자",
            "의무보유확약",
            "보호예수",
            "상장예정",
            "모집",
            "매출"
    );

    private static final List<String> NON_IPO_KEYWORDS = List.of(
            "타법인 주식 및 출자증권 양도결정",
            "타법인 주식 및 출자증권 취득결정",
            "단일판매",
            "공급계약",
            "최대주주 변경",
            "임원ㆍ주요주주",
            "주식등의대량보유상황보고서",
            "사업보고서",
            "반기보고서",
            "분기보고서"
    );

    // 원문 텍스트가 IPO 관련 문서 후보인지 판단하는 메서드
    public boolean isIpoCandidate(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalize(text);

        // 비 IPO 키워드가 있는지 확인
        boolean hasNonIpoKeyword = NON_IPO_KEYWORDS.stream()
                .anyMatch(normalized::contains);

        // IPO 관련 키워드가 있는지 확인
        boolean hasIpoKeyword = IPO_KEYWORDS.stream()
                .anyMatch(normalized::contains);

        if (hasNonIpoKeyword && ! hasIpoKeyword)
            return false;

        return hasIpoKeyword;
    }

    // 원문에서 발견된 IPO 관련 키워드 목록을 반환하는 메서드
    public List<String> findMatchedIpoKeywords(String text) {

        List<String> result = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return result;
        }

        String normalized = normalize(text);

        for (String keyword : IPO_KEYWORDS) {
            if (normalized.contains(keyword)) {
                result.add(keyword);
            }
        }

        return result;
    }

    // 공시 원문 텍스트를 보고 문서 성격을 Enum으로 판별하는 메서드
    public DisclosureDocumentType detectDocumentType(String text) {

        if (text == null || text.isBlank())
            return DisclosureDocumentType.UNKNOWN;

        String normalized = normalize(text);

        if (!isIpoCandidate(normalized))
            return DisclosureDocumentType.NON_IPO_DOCUMENT;

        // 발행조건확정 관련 문구가 있으면 확정조건 문서로 분류
        if (normalized.contains("발행조건확정")
                || normalized.contains("발행조건 및 가액이 확정")
                || normalized.contains("공모가액 확정")
                || normalized.contains("모집(매출) 확정가액"))
            return DisclosureDocumentType.FINAL_OFFERING_CONDITION;

        // 정정신고서 문구가 있으면 정정 증권신고서로 분류
        if (normalized.contains("정정신고서")
                || normalized.contains("정정대상 신고서")
                || normalized.contains("[정정]"))
            return DisclosureDocumentType.CORRECTION_SECURITIES_REPORT;

        // 투자설명서 문구가 있으면 투자설명서로 분류
        if (normalized.contains("투자설명서"))
            return DisclosureDocumentType.INVESTMENT_PROSPECTUS;

        // 증권신고서 문구가 있으면 최초 증권신고서로 분류
        if (normalized.contains("증권신고서"))
            return DisclosureDocumentType.INITIAL_SECURITIES_REPORT;

        return DisclosureDocumentType.UNKNOWN;
    }

    // DART 공시검색 API의 reportNm만 보고 문서 성격을 빠르게 추정하는 메서드
    public DisclosureDocumentType detectDocumentTypeFromReportName(String reportName) {

        if (reportName == null || reportName.isBlank())
            return DisclosureDocumentType.UNKNOWN;

        String normalized = normalize(reportName);

        // reportName에 발행조건확정 표시가 있으면 발행조건확정 문서로 판단 (정정 표시보다 우선)
        if (normalized.contains("발행조건확정"))
            return DisclosureDocumentType.FINAL_OFFERING_CONDITION;

        // reportName에 정정 표시가 있으면 정정신고서로 판단
        if (normalized.contains("[정정]")
                || normalized.contains("정정")
                || normalized.contains("기재정정"))
            return DisclosureDocumentType.CORRECTION_SECURITIES_REPORT;

        // 투자설명서로 판단
        if (normalized.contains("투자설명서"))
            return DisclosureDocumentType.INVESTMENT_PROSPECTUS;

        // 증권신고서이면 최초 증권신고서로 판단
        if (normalized.contains("증권신고서"))
            return DisclosureDocumentType.INITIAL_SECURITIES_REPORT;

        return DisclosureDocumentType.UNKNOWN;
    }

    // DART 공시검색 결과의 reportNm을 기준으로 공모주 관련 공시 후보인지 판단하는 메서드
    public boolean isIpoRelevantReportName(String reportName) {
        if (reportName == null || reportName.isBlank())
            return false;

        String name = reportName.trim();

        // 공모주와 직접 관련될 가능성이 높은 보고서명
        // 증권발행실적보고서·신규상장 공시는 상장일 등 청약 이후 확정 정보를 담음
        boolean hasIpoReportType =
                name.contains("증권신고서")
                        || name.contains("투자설명서")
                        || name.contains("발행조건확정")
                        || name.contains("증권발행실적보고서")
                        || name.contains("신규상장");

        // 공모주와 관련된 키워드
        // 보고서명이 길거나 정정 공시 형태일 때 보조 판단 기준으로 사용
        boolean hasIpoKeyword =
                name.contains("지분증권")
                        || name.contains("공모")
                        || name.contains("모집")
                        || name.contains("매출");

        // 명백히 IPO 공시가 아닌 주요사항보고서 계열은 제외
        boolean hasNonIpoKeyword =
                name.contains("타법인 주식")
                        || name.contains("출자증권")
                        || name.contains("양도결정")
                        || name.contains("취득결정")
                        || name.contains("주요사항보고서")
                        || name.contains("단일판매")
                        || name.contains("공급계약")
                        || name.contains("최대주주")
                        || name.contains("사업보고서")
                        || name.contains("반기보고서")
                        || name.contains("분기보고서");

        // 명백한 비 IPO 키워드가 있으면 제외
        if (hasNonIpoKeyword)
            return false;

        // IPO 성격의 보고서 유형이 있거나, 공모 관련 키워드가 있으면 후보로 봄
        return hasIpoReportType || hasIpoKeyword;
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

}
