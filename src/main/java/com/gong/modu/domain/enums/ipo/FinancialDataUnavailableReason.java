package com.gong.modu.domain.enums.ipo;

// 재무 차트 데이터가 없을 때 프론트가 표시할 수 있는 사유
public enum FinancialDataUnavailableReason {
    SPAC,
    DISCLOSURE_NOT_PARSED,
    FINANCIAL_TABLE_NOT_FOUND,
    NO_FINANCIAL_DATA
}
