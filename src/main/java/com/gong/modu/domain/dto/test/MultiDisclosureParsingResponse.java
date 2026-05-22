package com.gong.modu.domain.dto.test;

import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.domain.dto.pipeline.IpoDisclosureParsingResult;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
// 한 IPO의 모든 공시를 파싱한 뒤 문서별 결과와 누적 결과를 함께 확인하기 위한 테스트 응답 DTO
public class MultiDisclosureParsingResponse {

    // 대상 IPO 이벤트 ID (dry-run-ipo 호출 시)
    private Long ipoEventId;

    // 대상 기업 corp_code (dry-run-corp 호출 시)
    private String corpCode;

    // 처리한 공시 개수
    private int reportCount;

    // 공시별 파싱 결과 목록 (문서타입 우선순위 순)
    private List<PerDocumentResult> perDocumentResults;

    // 전체 공시에서 필드별 첫 non-null 값을 모은 누적 결과
    private IpoDisclosureParsingResult accumulatedResult;

    // 각 필드를 채운 공시 접수번호 (디버깅용)
    private Map<String, String> fieldSourceMap;

    @Getter
    @Builder
    public static class PerDocumentResult {

        // 공시 접수번호
        private String rceptNo;

        // 공시명
        private String reportName;

        // 원문 기준으로 판별한 문서 성격
        private DisclosureDocumentType detectedDocumentType;

        // AI에게 전달한 문맥 길이
        private int aiContextLength;

        // AI 파싱 결과 (원본)
        private AiDisclosureParsingResult aiDisclosureParsingResult;

        // AI 결과를 검증·변환한 최종 파싱 결과
        private IpoDisclosureParsingResult mergedParsingResult;

        // 파이프라인 실행에 실패한 경우의 사유 (성공 시 null)
        private String error;
    }
}
