package com.gong.modu.domain.enums.ipo;

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
    UNKNOWN;

    // 확정 정보가 풍부한 순서를 나타내는 우선순위 (낮을수록 우선)
    // 여러 공시 결과를 누적할 때 우선순위 높은 문서부터 순회하기 위해 사용
    // 발행조건확정/정정 문서는 수요예측 이후 확정값을 담으므로 최우선
    public int priority() {
        return switch (this) {
            case FINAL_OFFERING_CONDITION -> 1;
            case CORRECTION_SECURITIES_REPORT -> 2;
            case INVESTMENT_PROSPECTUS -> 3;
            case INITIAL_SECURITIES_REPORT -> 4;
            case UNKNOWN, NON_IPO_DOCUMENT -> 9;
        };
    }
}
