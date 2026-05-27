package com.gong.modu.domain.enums.ipo;

import io.swagger.v3.oas.annotations.media.Schema;

// 재무 차트 데이터가 없을 때 프론트가 표시할 수 있는 사유
@Schema(description = """
        재무 데이터가 없을 때의 사유 코드.
        - SPAC                      : 스팩이라 영업 재무지표가 사실상 없음
        - DISCLOSURE_NOT_PARSED     : 공시 원문 파싱 전 (다음 스케줄 후 자동 채워질 수 있음)
        - FINANCIAL_TABLE_NOT_FOUND : 원문 파싱은 됐으나 재무제표 표를 찾지 못함
        - NO_FINANCIAL_DATA         : 그 외 사유로 데이터 없음
        """)
public enum FinancialDataUnavailableReason {
    SPAC,
    DISCLOSURE_NOT_PARSED,
    FINANCIAL_TABLE_NOT_FOUND,
    NO_FINANCIAL_DATA
}
