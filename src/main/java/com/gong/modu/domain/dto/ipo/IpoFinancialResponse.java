package com.gong.modu.domain.dto.ipo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

// 재무 차트용 연도별 재무 하이라이트 응답 DTO
// year: bsns_year 값 그대로 반환 / 프론트에서 "제N기" 표현 처리
// 손실·자본잠식 등으로 음수 가능 → Long 타입 유지
@Schema(description = "공모주 재무제표 요약(3.1.3)의 한 사업연도 재무 하이라이트")
@Getter
@Builder
public class IpoFinancialResponse {

    @Schema(description = "사업연도 (DART bsnsYear 그대로). 프론트에서 \"제N기\" 표기로 가공.", example = "2024")
    private String year;

    @Schema(description = "매출액 (원 단위). SPAC 은 0 일 수 있음. 공시에서 미발견 시 null.",
            example = "12345000000", nullable = true)
    private Long revenue;

    @Schema(description = "영업이익 (원 단위). 손실 시 음수. 공시에서 미발견 시 null.",
            example = "-2946000000", nullable = true)
    private Long operatingProfit;

    @Schema(description = "당기순이익 (원 단위). 순손실 시 음수. 공시에서 미발견 시 null.",
            example = "-1844000000", nullable = true)
    private Long netIncome;

    @Schema(description = "자산총계 (원 단위). 공시에서 미발견 시 null.",
            example = "10940000000", nullable = true)
    private Long totalAssets;

    @Schema(description = "부채총계 (원 단위). 공시에서 미발견 시 null.",
            example = "1300000000", nullable = true)
    private Long totalLiabilities;

    @Schema(description = "자본총계 (원 단위). 자본잠식 시 음수. 공시에서 미발견 시 null.",
            example = "9640000000", nullable = true)
    private Long totalEquity;
}
