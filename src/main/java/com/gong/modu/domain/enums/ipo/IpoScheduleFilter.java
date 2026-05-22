package com.gong.modu.domain.enums.ipo;

// 홈 화면 청약 일정 리스트의 필터 구분 Enum
// 기능명세서 3.1.1 청약 일정 리스트업 필터 항목 (수요예측/청약/락업해제/환불/상장/배정)
public enum IpoScheduleFilter {
    DEMAND_FORECAST, // 수요예측 (수요예측 시작일 기준)
    SUBSCRIPTION,    // 청약 (청약 시작일 기준)
    LOCKUP_EXPIRY,   // 락업해제 (보호예수 해제일 기준)
    REFUND,          // 환불 (환불일 기준)
    LISTING,         // 상장 (상장일 기준)
    ALLOCATION       // 배정 (배정일 기준)
}
