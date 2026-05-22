package com.gong.modu.domain.dto.test;

import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.domain.dto.pipeline.IpoDisclosureParsingResult;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
// 정규식 파싱, AI 보완 파싱, 병합 결과를 한 번에 확인하기 위한 테스트 응답 DTO
public class AiDisclosureParsingResponse {

    // 파싱 대상 공시 접수번호
    private String rceptNo;

    // 원문 텍스트 전체 길이
    private int originalTextLength;

    // AI에게 실제로 전달한 문맥 길이
    private int aiContextLength;

    // 원문 앞부분 미리보기
    private String originalTextPreview;

    // AI에게 전달한 문맥 미리보기
    private String aiContextPreview;

    // IPO 관련 문서 후보인지 여부
    private boolean ipoDocumentCandidate;

    // 문서 성격 Enum
    private DisclosureDocumentType detectedDocumentType;

    // 원문에서 발견한 IPO 관련 키워드 목록
    private List<String> matchedKeywords;

    // AI 문맥으로 선택된 섹션 정보
    private List<SelectedSectionDto> selectedSections;

    // AI 파싱 결과 (원본)
    private AiDisclosureParsingResult aiDisclosureParsingResult;

    // AI 결과를 검증·변환한 최종 파싱 결과
    private IpoDisclosureParsingResult mergedParsingResult;

    @Getter
    @Builder
    public static class SelectedSectionDto {

        // 섹션 타입
        private String sectionType;

        // 섹션 추출 기준 키워드
        private String matchedKeyword;

        // 원문 시작 위치
        private int startIndex;

        // 원문 종료 위치
        private int endIndex;

        // 섹션 텍스트 길이
        private int contentLength;

        private int score;

        // 섹션 미리보기
        private String preview;
    }
}