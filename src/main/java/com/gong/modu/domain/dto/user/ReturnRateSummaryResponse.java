package com.gong.modu.domain.dto.user;

import com.gong.modu.domain.enums.ipo.ReturnRateTrend;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 청약이력 기반 평균 수익률 요약 응답 DTO (기능명세서 3.3)
// 월별 평균 수익률 + 이번달/저번달 비교 트렌드를 반환
@Getter
@Builder
@Schema(description = "청약이력 기반 평균 수익률 요약 (기능명세서 3.3). 월별 꺾은선 + 이번달/저번달 비교 데이터 제공")
public class ReturnRateSummaryResponse {

    @Schema(description = "조회 기간 (개월). 요청한 months 값", example = "6")
    private int months;

    @Schema(description = "월별 평균 수익률 목록. 매도 완료 이력이 있는 월만 포함, 매도일(sellDate) 기준 오름차순")
    private List<MonthlyReturnRate> monthlyReturnRates;

    @Schema(description = "이번달 평균 수익률 (%). 이번달에 매도 완료 이력이 없으면 null", example = "31.00")
    private BigDecimal currentMonthReturnRate;

    @Schema(description = "저번달 평균 수익률 (%). 저번달에 매도 완료 이력이 없으면 null", example = "20.00")
    private BigDecimal lastMonthReturnRate;

    @Schema(description = """
                    이번달 평균 수익률 vs 저번달 평균 수익률 비교 결과. 프론트는 이 값으로 안내 메시지를 분기합니다.

                    - INCREASED: 이번달 평균 수익률이 저번달보다 높음 (예: "저번달에 비해 이번달 평균 수익률이 증가했어요!")
                    - DECREASED: 이번달 평균 수익률이 저번달보다 낮음 (예: "저번달에 비해 이번달 평균 수익률이 감소했어요!")
                    - UNCHANGED: 이번달 평균 수익률이 저번달과 동일 (예: "저번달과 이번달 평균 수익률이 같아요")
                    - NO_DATA: 이번달 또는 저번달에 매도 완료 이력이 없어 비교 불가 (currentMonthReturnRate 또는 lastMonthReturnRate 중 하나 이상이 null인 경우)
                    """,
            example = "INCREASED",
            allowableValues = {"INCREASED", "DECREASED", "UNCHANGED", "NO_DATA"})
    private ReturnRateTrend trend;

    @Getter
    @Builder
    @Schema(description = "월별 평균 수익률 단건")
    public static class MonthlyReturnRate {

        @Schema(description = "연도", example = "2026")
        private int year;

        @Schema(description = "월 (1~12)", example = "5")
        private int month;

        @Schema(description = "해당 월의 평균 수익률 (%). 소수점 둘째자리까지", example = "31.00")
        private BigDecimal averageReturnRate;

        @Schema(description = "평균 계산에 포함된 매도 완료 이력 건수 (필수 값 누락 이력은 제외)", example = "3")
        private int recordCount;
    }
}
