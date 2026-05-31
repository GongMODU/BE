package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.FinancialDataUnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "공모주 재무제표 요약 데이터 + 미가용 사유. /financials 와 동일 데이터에 available/reason/message 를 더해 반환")
@Getter
@Builder
public class IpoFinancialStatusResponse {

    @Schema(description = "재무 하이라이트 배열. available=false 일 때는 빈 배열.")
    private List<IpoFinancialResponse> financials;

    @Schema(description = "재무 데이터 가용 여부. true 면 financials 가 비어있지 않음.", example = "true")
    private boolean available;

    @Schema(description = """
            available=false 일 때만 값이 들어옴. 프론트는 이 값으로 안내 메시지를 분기.
            - SPAC                      : 스팩이라 영업 재무지표가 사실상 없음
            - DISCLOSURE_NOT_PARSED     : 공시 원문 파싱 전 (다음 스케줄 후 자동 채워질 수 있음)
            - FINANCIAL_TABLE_NOT_FOUND : 원문 파싱은 됐으나 재무제표 표를 찾지 못함
            - NO_FINANCIAL_DATA         : 그 외 사유로 데이터 없음
            """,
            nullable = true,
            example = "FINANCIAL_TABLE_NOT_FOUND")
    private FinancialDataUnavailableReason unavailableReason;

    @Schema(description = "프론트에 그대로 노출 가능한 안내 문구. 데이터가 있을 때도 짧은 설명이 들어감.",
            example = "공시 원문에서 재무제표 표를 찾지 못했습니다.")
    private String message;
}
