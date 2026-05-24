package com.gong.modu.service.pipeline;

import com.gong.modu.domain.dto.pipeline.DisclosureSection;
import com.gong.modu.domain.enums.ipo.DisclosureSectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
// AI에게 보낼 공시 원문 일부 문맥을 추출하는 클래스
public class DisclosureContextExtractor {
    // AI에게 전달할 최대 컨텍스트 길이
    // 한글 30,000자에 6개 섹션을 다 담으려니 OFFERING_PROCEDURE 등 후순위 섹션이 잘리는 케이스 다수 발생
    // 60,000자로 늘려도 Claude Sonnet 입력 비용은 공시 1건당 약 $0.08 수준이라 부담 적음
    // (lost-in-the-middle 영향을 고려해 무한정 키우지 않고 적정선 유지)
    private static final int MAX_CONTEXT_LENGTH = 60_000;

    // 같은 섹션 타입 간 중복 허용 임계값 (80% 이상 겹치면 제외)
    private static final double SAME_TYPE_OVERLAP_THRESHOLD = 0.8;

    // 다른 섹션 타입 간 중복 허용 임계값 (50% 이상 겹치면 제외)
    private static final double CROSS_TYPE_OVERLAP_THRESHOLD = 0.5;

    // AI 파싱이 모든 필드를 추출하므로 항상 전체 섹션 타입을 대상으로 함
    private static final List<DisclosureSectionType> ALL_SECTION_TYPES = List.of(
            DisclosureSectionType.OFFERING_OVERVIEW,
            DisclosureSectionType.OFFERING_PROCEDURE,
            DisclosureSectionType.BOOKBUILDING_RESULT,
            DisclosureSectionType.OFFER_PRICE_DECISION,
            DisclosureSectionType.LOCKUP,
            DisclosureSectionType.UNDERWRITER
    );

    // 섹션 추출 실패 시 fallback으로 사용할 키워드 목록
    private static final List<String> FALLBACK_KEYWORDS = List.of(
            "공모개요",
            "수요예측",
            "수요예측 결과",
            "모집 또는 매출절차",
            "청약기일",
            "환불",
            "상장예정일",
            "매매개시일",
            "희망공모가",
            "확정공모가",
            "보호예수",
            "의무보유확약",
            "상장예정주식수"
    );

    // 잘리지 않도록 컨텍스트 맨 앞에 무조건 주입할 핵심 키워드들
    // (특히 상장예정일·매매개시일은 OFFERING_PROCEDURE 섹션 우선순위가 낮아서 자주 잘림)
    // 동일 키워드가 규정 텍스트에 수십 번 나오므로, 윈도우에 실제 한국 날짜 패턴이 포함된 것만 채택
    private static final List<String> CRITICAL_KEYWORDS = List.of(
            "상장예정일",
            "상장 예정일",
            "상장(예정)일",
            "매매개시일",
            "신규상장일",
            "신규상장",
            "상장일",
            "공모일정",
            "주요일정",
            "주요 일정",
            "공모 일정"
    );

    // 핵심 키워드 주변에 잡을 윈도우 크기 (앞 300자 + 뒤 1500자)
    private static final int CRITICAL_WINDOW_BEFORE = 300;
    private static final int CRITICAL_WINDOW_AFTER = 1500;

    // 핵심 키워드 컨텍스트가 사용할 최대 예산 (전체 60000자 중 일부 우선 할당)
    private static final int CRITICAL_CONTEXT_BUDGET = 8000;

    // 한국 IPO 공시에서 자주 쓰이는 날짜 표기 패턴
    // yyyy년 m월 d일 / yyyy.m.d / yyyy-m-d / yyyy/m/d 모두 매치
    private static final Pattern KOREAN_DATE_PATTERN = Pattern.compile(
            "\\d{4}\\s*[년./\\-]\\s*\\d{1,2}\\s*[월./\\-]\\s*\\d{1,2}\\s*일?"
    );

    private final DisclosureSectionExtractor disclosureSectionExtractor;

    // 공시 원문에서 AI 파싱에 필요한 섹션 문맥을 추출하는 메서드
    public String extractContext(String originalText) {
        if (originalText == null || originalText.isBlank())
            return "";

        // 1) 상장예정일/매매개시일 등 핵심 키워드 주변을 먼저 뽑아 컨텍스트 맨 앞에 고정 배치
        //    (섹션 추출 우선순위에 밀려 잘리는 케이스 방어)
        String criticalContext = extractCriticalKeywordContext(originalText);

        List<DisclosureSection> sections =
                disclosureSectionExtractor.extractSectionByTypes(originalText, ALL_SECTION_TYPES);

        if (sections.isEmpty()) {
            String fallback = extractFallbackKeywordContext(originalText);
            return prependCriticalContext(criticalContext, fallback);
        }

        List<DisclosureSection> selected = selectNonOverlappingSections(sections);

        Set<String> contextBlocks = new LinkedHashSet<>();
        for (DisclosureSection section : selected) {
            contextBlocks.add(buildSectionBlock(section));
        }

        String joined = String.join("\n\n--- SECTION SPLIT ---\n\n", contextBlocks);

        // 섹션 추출 후 남은 예산으로 문서 후반부를 추가해 미발견 구간을 커버
        joined = appendTailCoverageIfBudgetRemains(joined, originalText, selected);

        String withCritical = prependCriticalContext(criticalContext, joined);

        if (withCritical.length() > MAX_CONTEXT_LENGTH) {
            return withCritical.substring(0, MAX_CONTEXT_LENGTH);
        }

        return withCritical;
    }

    // 핵심 키워드(상장예정일/매매개시일 등) 주변 텍스트를 모아 컨텍스트 맨 앞에 붙이는 메서드
    // 윈도우 안에 실제 한국 날짜 패턴이 있는 경우만 채택 (규정·법령 인용문 필터링)
    private String extractCriticalKeywordContext(String originalText) {
        String normalized = normalize(originalText);

        Set<String> windows = new LinkedHashSet<>();

        for (String keyword : CRITICAL_KEYWORDS) {
            int fromIndex = 0;
            while (fromIndex < normalized.length()) {
                int keywordIndex = normalized.indexOf(keyword, fromIndex);
                if (keywordIndex < 0) break;

                int start = Math.max(0, keywordIndex - CRITICAL_WINDOW_BEFORE);
                int end = Math.min(normalized.length(), keywordIndex + CRITICAL_WINDOW_AFTER);
                String window = normalized.substring(start, end);

                // 윈도우 안에 한국 날짜 패턴이 있어야만 채택 (실제 일정표 vs 규정 텍스트 구분)
                if (KOREAN_DATE_PATTERN.matcher(window).find()) {
                    windows.add(window);
                }

                fromIndex = keywordIndex + keyword.length();
            }
        }

        if (windows.isEmpty()) return "";

        String joined = String.join("\n---\n", windows);

        if (joined.length() > CRITICAL_CONTEXT_BUDGET) {
            joined = joined.substring(0, CRITICAL_CONTEXT_BUDGET);
        }

        return "[CRITICAL_KEYWORDS: 일정표(상장예정일/매매개시일/공모일정 + 날짜 포함) 발췌]\n" + joined;
    }

    // 핵심 키워드 컨텍스트를 메인 컨텍스트 앞에 결합하는 메서드
    private String prependCriticalContext(String criticalContext, String mainContext) {
        if (criticalContext == null || criticalContext.isEmpty()) {
            return mainContext;
        }
        if (mainContext == null || mainContext.isEmpty()) {
            return criticalContext;
        }
        return criticalContext + "\n\n=== END OF CRITICAL KEYWORDS ===\n\n" + mainContext;
    }

    // 디버깅용: 실제로 AI에게 전달되는 섹션 목록과 동일한 필터를 적용해 반환
    public List<DisclosureSection> extractSelectedSections(String originalText) {
        if (originalText == null || originalText.isBlank())
            return List.of();

        List<DisclosureSection> sections =
                disclosureSectionExtractor.extractSectionByTypes(originalText, ALL_SECTION_TYPES);

        return selectNonOverlappingSections(sections);
    }

    // 겹침 임계값을 타입별로 다르게 적용해 섹션을 선별하는 메서드
    // - 같은 타입끼리는 80% 이상 겹쳐야 제외 (경쟁률 섹션과 확약 섹션처럼 일부 겹쳐도 다른 내용 포함 가능)
    // - 다른 타입끼리는 50% 이상 겹치면 제외 (중복 컨텍스트 방지)
    private List<DisclosureSection> selectNonOverlappingSections(List<DisclosureSection> sections) {
        List<DisclosureSection> included = new ArrayList<>();

        for (DisclosureSection candidate : sections) {
            boolean sameTypeAlreadyIncluded = included.stream()
                    .anyMatch(s -> s.getSectionType() == candidate.getSectionType());

            double threshold = sameTypeAlreadyIncluded
                    ? SAME_TYPE_OVERLAP_THRESHOLD
                    : CROSS_TYPE_OVERLAP_THRESHOLD;

            if (!isOverlapExcessive(candidate, included, threshold)) {
                included.add(candidate);
            }
        }

        return included;
    }

    // 새 섹션이 이미 포함된 섹션들과 threshold 비율 이상 겹치는지 판단
    private boolean isOverlapExcessive(
            DisclosureSection candidate,
            List<DisclosureSection> included,
            double threshold
    ) {
        int newStart = candidate.getStartIndex();
        int newEnd = candidate.getEndIndex();
        int newLen = newEnd - newStart;

        if (newLen <= 0) return false;

        for (DisclosureSection existing : included) {
            int overlapStart = Math.max(newStart, existing.getStartIndex());
            int overlapEnd = Math.min(newEnd, existing.getEndIndex());
            int overlapLen = overlapEnd - overlapStart;

            if (overlapLen > 0 && (double) overlapLen / newLen >= threshold) {
                return true;
            }
        }

        return false;
    }

    // 선택된 섹션 이후 문서 구간을 남은 예산만큼 추가해 후반부 커버
    private String appendTailCoverageIfBudgetRemains(
            String joined,
            String originalText,
            List<DisclosureSection> selected
    ) {
        int remainingBudget = MAX_CONTEXT_LENGTH - joined.length() - 300;
        if (remainingBudget < 1000) return joined;

        String normalizedFull = normalize(originalText);

        int maxEndIndex = selected.stream()
                .mapToInt(DisclosureSection::getEndIndex)
                .max()
                .orElse(0);

        // 이미 포함된 섹션 바로 다음부터 시작하되, 예산에 맞게 시작점 조정
        int tailFromEnd = normalizedFull.length() - remainingBudget;
        int tailStart = Math.max(maxEndIndex + 200, tailFromEnd);

        if (tailStart >= normalizedFull.length() - 500) return joined;

        String tailContent = normalizedFull.substring(tailStart);
        String tailBlock = "[SECTION_TYPE]\nGENERAL\n\n[CONTENT]\n" + tailContent;

        return joined + "\n\n--- SECTION SPLIT ---\n\n" + tailBlock;
    }

    // AI에게 전달할 섹션 블록 문자열을 만드는 메서드
    private String buildSectionBlock(DisclosureSection section) {
        return """
               [SECTION_TYPE]
               %s

               [MATCHED_KEYWORD]
               %s

               [CONTENT]
               %s
               """.formatted(
                section.getSectionType().name(),
                section.getMatchedKeyword(),
                section.getContent()
        );
    }

    // 섹션 추출이 실패했을 때 사용하는 fallback 문맥 추출 메서드
    private String extractFallbackKeywordContext(String originalText) {
        String normalized = normalize(originalText);

        Set<String> windows = new LinkedHashSet<>();

        for (String keyword : FALLBACK_KEYWORDS)
            windows.addAll(findWindows(normalized, keyword));

        String joined = String.join("\n\n--- FALLBACK CONTEXT SPLIT ---\n\n", windows);

        if (joined.length() > MAX_CONTEXT_LENGTH)
            return joined.substring(0, MAX_CONTEXT_LENGTH);

        return joined;
    }

    // 특정 키워드 주변 문맥 조각들을 찾는 fallback용 메서드
    private List<String> findWindows(String text, String keyword) {
        List<String> result = new ArrayList<>();

        if (text == null || text.isBlank() || keyword == null || keyword.isBlank()) {
            return result;
        }

        int windowBefore = 500;
        int windowAfter = 2500;
        int fromIndex = 0;

        while (fromIndex < text.length()) {
            int keywordIndex = text.indexOf(keyword, fromIndex);

            if (keywordIndex < 0) break;

            int start = Math.max(0, keywordIndex - windowBefore);
            int end = Math.min(text.length(), keywordIndex + windowAfter);

            result.add(text.substring(start, end));

            fromIndex = keywordIndex + keyword.length();
        }

        return result;
    }

    // 원문 텍스트의 특수 토큰과 공백을 정리하는 메서드
    private String normalize(String text) {
        return text
                .replaceAll("&cr;", "\n")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
