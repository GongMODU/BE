package com.gong.modu.domain.enums.ipo;

// 이번달 평균 수익률 vs 저번달 평균 수익률 비교 결과를 표현하는 Enum
// 백엔드는 enum만 내려주고 프론트가 메시지("저번달에 비해 이번달 평균 수익률이 증가했어요!" 등) 처리
public enum ReturnRateTrend {

    // 이번달 평균 수익률이 저번달보다 높음
    INCREASED,

    // 이번달 평균 수익률이 저번달보다 낮음
    DECREASED,

    // 이번달 평균 수익률이 저번달과 동일
    UNCHANGED,

    // 이번달 또는 저번달에 매도 완료 이력이 없어 비교 불가
    NO_DATA
}
