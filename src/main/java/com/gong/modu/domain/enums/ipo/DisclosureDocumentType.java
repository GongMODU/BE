package com.likelion14.PBL_Spring.member.domain;

// DART 공시 원문 문서가 어떤 성격의 문서인지 표현하는 Enum
public enum DisclosureDocumentType {

    // 최초 증권신고서 (희망공모가, 공모주식수, 청약 일정 등)
    INITIAL_SECURITIES_REPORT,

    // 정정 증권신고서 (수요예측 이후 제출된 거라면 확정공모가, 기관경쟁률, 의무보유확약 등 있을 가능성 있음)
    CORRECTION_SECURITIES_REPORT,

    // 발행조건확정 문서 (공모가액, 모집총액 등 발행 조건이 최종 확정되었을 때 삽입되거나 함께 제공되는 문서)
    FINAL_OFFERING_CONDITION,

    // 투자설명서 (투자자에게 제공되는 최종 설명 문서)
    INVESTMENT_PROSPECTUS,

    // IPO, 공모주와 관련 없는 공시 문서 (사업보고서, 반기보고서, 단일판매 공급계약, 타법인 주식 취득/양도 등)
    NON_IPO_DOCUMENT,

    // 문서 성격을 판단하지 못한 경우
    UNKNOWN
}
