package com.gong.modu.domain.dto.pipeline;

import com.gong.modu.domain.enums.ipo.DisclosureSectionType;
import lombok.Builder;
import lombok.Getter;

// 공시 원문에서 추출한 특정 섹션 조각을 담는 DTO
@Getter
@Builder
public class DisclosureSection {

    private DisclosureSectionType sectionType; // 섹션 타입

    private String matchedKeyword; // 섹션 추출의 기준 키워드

    private int startIndex; // 원문 전체에서 이 섹션 조각이 시작되는 위치

    private int endIndex; // 원문 전체에서 이 섹션 조각이 끝나는 위치

    private String content; // 실제 AI에게 전달할 섹션 텍스트

    private int priority; // 섹션의 우선순위

    private int score; // 해당 섹션이 실제 값 추출에 얼마나 유용해 보이는지 나타내는 점수
}
