package com.gong.modu.domain.enums.ipo;

// 공시 원문에서 AI 파싱에 사용할 주요 섹션의 종류를 표현하는 Enum
public enum DisclosureSectionType {

    // 공모 개요 섹션 (희망공모가, 확정공모가, 공모주식수, 모집총액, 청약 일정 등)
    OFFERING_OVERVIEW,

    // 모집 또는 매출의 절차 섹션 (청약일, 환불일, 납입일, 배정공고일, 상장예정일 등)
    OFFERING_PROCEDURE,

    // 수요예측 결과 섹션 (기관경쟁률, 확정공모가, 의무보유확약 비율 등)
    BOOKBUILDING_RESULT,

    // 공모가격 결정방법 섹션 (희망공모가 산정 근거, 확정공모가 산정 근거 등)
    OFFER_PRICE_DECISION,

    // 보호예수, 의무보유, 매각제한 관련 섹션 (보호예수 대상, 보호예수 기간, 해제일, 계속보유의무 등)
    LOCKUP,

    // 인수인 관련 섹션 (인수기관, 의무인수, 주관사 관련 내용)
    UNDERWRITER,

    // 위 섹션으로 명확히 분류하지 못했지만 AI에게 참고용으로 줄 수 있는 기타 문맥
    GENERAL
}
