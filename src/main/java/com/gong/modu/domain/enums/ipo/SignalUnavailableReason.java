package com.gong.modu.domain.enums.ipo;

// signalLevel이 null로 내려가는 사유를 표현하는 Enum
// 프론트가 "수요예측 후 확정", "스팩 공모" 같은 안내를 분기 처리할 때 사용
public enum SignalUnavailableReason {

    // 스팩(기업인수목적회사)이라 수요예측 자체가 없음 → 신호등 산출 대상 아님
    SPAC,

    // 수요예측 전이라 지표가 아직 산출되지 않음 → 수요예측 후 자동 채워짐
    PRE_DEMAND_FORECAST,

    // 수요예측은 끝났지만 AI 파싱에서 지표를 못 뽑은 드문 케이스 → 재파싱 필요
    INCOMPLETE_DATA
}
