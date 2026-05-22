package com.gong.modu.service.pipeline;

import com.gong.modu.domain.dto.pipeline.DisclosureSection;
import com.gong.modu.domain.enums.ipo.DisclosureSectionType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
// 공시 원문 전체에서 AI 파싱에 유용한 섹션 후보를 찾아내는 클래스
public class DisclosureSectionExtractor {

    // 키워드 앞쪽 문맥
    private static final int SECTION_BEFORE = 500;

    // 키워드 뒤쪽 문맥
    private static final int SECTION_AFTER = 10_000;

    // 한 섹션 타입당 최종 선택할 후보 수
    // 보호예수 섹션이 점수 경쟁에서 밀려 누락되는 것을 막기 위해 3으로 설정
    private static final int MAX_SECTIONS_PER_TYPE = 3;

    private static final Map<DisclosureSectionType, List<String>> SECTION_KEYWORDS = new LinkedHashMap<>();

    static {
        SECTION_KEYWORDS.put(
                DisclosureSectionType.OFFERING_OVERVIEW,
                List.of(
                        "공모개요",
                        "공모 개요",
                        "1. 공모개요",
                        "모집 또는 매출에 관한 일반사항",
                        "모집 또는 매출의 개요",
                        "모집 또는 매출 증권의 종류",
                        "모집(매출)가액",
                        "모집(매출) 확정가액",
                        "희망 공모가액",
                        "희망공모가",
                        "확정공모가"
                )
        );

        SECTION_KEYWORDS.put(
                DisclosureSectionType.OFFERING_PROCEDURE,
                List.of(
                        "모집 또는 매출절차",
                        "모집 또는 매출 절차",
                        "모집 또는 매출의 절차",
                        "4. 모집 또는 매출절차",
                        "청약 및 배정에 관한 사항",
                        "수요예측 기간",
                        "수요예측일",
                        "수요예측 기일",
                        "청약기일",
                        "청약일",
                        "환불일",
                        "납입일",
                        "상장예정일",
                        "상장일",
                        "신규상장",
                        "매매개시일"
                )
        );

        SECTION_KEYWORDS.put(
                DisclosureSectionType.BOOKBUILDING_RESULT,
                List.of(
                        "수요예측 결과",
                        "수요예측결과",
                        "수요예측 신청현황",
                        "수요예측 참여내역",
                        "기관투자자 수요예측",
                        "기관투자자 경쟁률",
                        "의무보유확약",
                        "의무보유 확약",
                        "확약비율"
                )
        );

        SECTION_KEYWORDS.put(
                DisclosureSectionType.OFFER_PRICE_DECISION,
                List.of(
                        "공모가격 결정방법",
                        "공모가격의 결정방법",
                        "공모가액 결정방법",
                        "공모가액의 결정방법",
                        "공모가격 산정",
                        "공모가 산정",
                        "희망 공모가액 산출",
                        "확정 공모가액"
                )
        );

        SECTION_KEYWORDS.put(
                DisclosureSectionType.LOCKUP,
                List.of(
                        "보호예수",
                        "의무보유",
                        "계속보유",
                        "매각제한",
                        "매각 제한",
                        "보호예수기간",
                        "의무보유기간",
                        "보호예수 해제",
                        "의무보유 해제",
                        "최대주주등의 주식 계속보유",
                        "최대주주등의 매각제한",
                        "의무보유 확약기간",
                        "유통가능",
                        "상장 후 유통가능",
                        "상장예정주식수"
                )
        );

        SECTION_KEYWORDS.put(
                DisclosureSectionType.UNDERWRITER,
                List.of(
                        "인수 등에 관한 사항",
                        "인수인의 의견",
                        "인수인의 의무인수",
                        "대표주관회사",
                        "공동대표주관회사",
                        "인수기관"
                )
        );
    }

    public List<DisclosureSection> extractSection(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            return List.of();
        }

        String normalized = normalize(originalText);

        List<DisclosureSection> allCandidates = new ArrayList<>();

        for (Map.Entry<DisclosureSectionType, List<String>> entry : SECTION_KEYWORDS.entrySet()) {
            DisclosureSectionType sectionType = entry.getKey();

            for (String keyword : entry.getValue()) {
                allCandidates.addAll(findSectionByKeyword(normalized, sectionType, keyword));
            }
        }

        // 섹션 타입별로 묶은 뒤, 점수가 높은 후보만 남김
        Map<DisclosureSectionType, List<DisclosureSection>> grouped = new LinkedHashMap<>();

        for (DisclosureSection section : allCandidates) {
            grouped.computeIfAbsent(section.getSectionType(), key -> new ArrayList<>())
                    .add(section);
        }

        List<DisclosureSection> selected = new ArrayList<>();

        for (List<DisclosureSection> sections : grouped.values()) {
            sections.stream()
                    .sorted(
                            Comparator.comparingInt(DisclosureSection::getScore).reversed()
                                    .thenComparingInt(DisclosureSection::getStartIndex)
                    )
                    .limit(MAX_SECTIONS_PER_TYPE)
                    .forEach(selected::add);
        }

        return selected.stream()
                .sorted(
                        Comparator.comparingInt(DisclosureSection::getPriority)
                                .thenComparing(Comparator.comparingInt(DisclosureSection::getScore).reversed())
                                .thenComparingInt(DisclosureSection::getStartIndex)
                )
                .toList();
    }

    public List<DisclosureSection> extractSectionByTypes(
            String originalText,
            List<DisclosureSectionType> requiredTypes
    ) {
        if (requiredTypes == null || requiredTypes.isEmpty()) {
            return List.of();
        }

        return extractSection(originalText).stream()
                .filter(section -> requiredTypes.contains(section.getSectionType()))
                .toList();
    }

    private List<DisclosureSection> findSectionByKeyword(
            String text,
            DisclosureSectionType sectionType,
            String keyword
    ) {
        List<DisclosureSection> result = new ArrayList<>();

        if (text == null || text.isBlank() || keyword == null || keyword.isBlank()) {
            return result;
        }

        int fromIndex = 0;

        while (fromIndex < text.length()) {
            int keywordIndex = text.indexOf(keyword, fromIndex);

            if (keywordIndex < 0) {
                break;
            }

            int start = Math.max(0, keywordIndex - SECTION_BEFORE);
            int end = Math.min(text.length(), keywordIndex + SECTION_AFTER);

            String content = text.substring(start, end);

            int score = calculateScore(sectionType, content, keywordIndex, text.length());

            result.add(
                    DisclosureSection.builder()
                            .sectionType(sectionType)
                            .matchedKeyword(keyword)
                            .startIndex(start)
                            .endIndex(end)
                            .content(content)
                            .priority(priorityOf(sectionType))
                            .score(score)
                            .build()
            );

            fromIndex = keywordIndex + keyword.length();
        }

        return result;
    }

    // 섹션 후보가 실제 값 추출에 유용한지 점수화하는 메서드
    private int calculateScore(
            DisclosureSectionType sectionType,
            String content,
            int keywordIndex,
            int fullTextLength
    ) {
        int score = 0;

        // 너무 앞쪽은 목차/요약/정정표일 가능성이 높으므로 감점
        if (keywordIndex < fullTextLength * 0.15) {
            score -= 20;
        }

        // 문서 중후반부는 실제 본문 섹션일 가능성이 높아 가점
        if (keywordIndex > fullTextLength * 0.25) {
            score += 15;
        }

        // 목차성 문맥 감점
        if (containsAny(content, "목차", "제1부", "제2부", "요약정보의 모든 정정사항", "아래 본문의 정정사항")) {
            score -= 20;
        }

        // 예정/향후 결정 문맥은 실제 결과값이 아닐 가능성이 높아 감점
        if (containsAny(content, "결정할 예정", "제출할 예정", "변경될 수 있습니다", "확정할 예정")) {
            score -= 15;
        }

        switch (sectionType) {
            case BOOKBUILDING_RESULT -> {
                if (containsAny(content, "참여건수", "신청수량", "경쟁률", "의무보유확약", "확약비율")) {
                    score += 40;
                }
                if (content.matches("(?s).*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:대|:)\\s*1.*")) {
                    score += 50;
                }
                if (content.contains("%")) {
                    score += 10;
                }
            }
            case OFFERING_PROCEDURE -> {
                if (containsAny(content, "청약기일", "납입기일", "환불일", "배정공고일", "상장예정일", "매매개시일")) {
                    score += 40;
                }
                if (content.matches("(?s).*20\\d{2}\\s*[.년-]\\s*\\d{1,2}\\s*[.월-]\\s*\\d{1,2}.*")) {
                    score += 20;
                }
            }
            case OFFERING_OVERVIEW -> {
                if (containsAny(content, "증권수량", "모집(매출)가액", "모집(매출)총액", "기명식보통주")) {
                    score += 35;
                }
                if (containsAny(content, "확정가액", "확정공모가액")) {
                    score += 20;
                }
            }
            case OFFER_PRICE_DECISION -> {
                if (containsAny(content, "희망 공모가액", "확정 공모가액", "공모가격 산정", "PER", "비교회사")) {
                    score += 35;
                }
            }
            case LOCKUP -> {
                if (containsAny(content, "보호예수", "의무보유", "계속보유", "매각제한", "해제", "상장일로부터")) {
                    score += 40;
                }
                if (content.matches("(?s).*\\d+\\s*(?:개월|년).*")) {
                    score += 20;
                }
                if (containsAny(content, "상장예정주식수", "유통가능")) {
                    score += 20;
                }
            }
            case UNDERWRITER -> {
                if (containsAny(content, "대표주관회사", "공동대표주관회사", "인수수량", "인수금액")) {
                    score += 25;
                }
            }
            case GENERAL -> score += 0;
        }

        return score;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private int priorityOf(DisclosureSectionType sectionType) {
        return switch (sectionType) {
            case BOOKBUILDING_RESULT -> 1;
            case OFFERING_OVERVIEW -> 2;
            case OFFERING_PROCEDURE -> 3;
            case OFFER_PRICE_DECISION -> 4;
            case LOCKUP -> 5;
            case UNDERWRITER -> 6;
            case GENERAL -> 9;
        };
    }

    private String normalize(String text) {
        return text
                .replace("&cr;", "\n")
                .replaceAll("\\s+", " ")
                .trim();
    }
}