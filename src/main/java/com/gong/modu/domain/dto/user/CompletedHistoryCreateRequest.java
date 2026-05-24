package com.gong.modu.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// 4.1 과거 투자 이력 입력 요청 (DB 종목 조회 없이 사용자 직접 입력)
// 등록 시 record_status = COMPLETED 로 저장
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "4.1 과거 투자 이력 생성 요청 (DB 종목 조회 없이 직접 입력, COMPLETED 상태로 저장)")
public class CompletedHistoryCreateRequest {

    @Schema(description = "종목명 (필수, 사용자 직접 입력)", example = "테스트종목", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 200)
    private String inputStockName;

    @Schema(description = "회사명 (선택, 사용자 직접 입력)", example = "테스트회사")
    @Size(max = 200)
    private String inputCompanyName;

    @Schema(description = "청약한 증권사", example = "삼성증권")
    @Size(max = 100)
    private String securityCompany;

    @Schema(description = "청약 신청 주식 수", example = "100")
    @PositiveOrZero
    private Long subscribedQuantity;

    @Schema(description = "실제 배정받은 주식 수", example = "10")
    @PositiveOrZero
    private Long allocatedQuantity;

    @Schema(description = "공모가(1주당). 수익률 계산의 기준값이므로 반드시 입력 권장. 누락 시 수익률 계산에서 제외됨",
            example = "20000")
    @PositiveOrZero
    private BigDecimal offerPrice;

    @Schema(description = "매도 단가", example = "50000")
    @PositiveOrZero
    private BigDecimal sellPrice;

    @Schema(description = "청약 또는 매도 수수료", example = "500")
    @PositiveOrZero
    private BigDecimal fee;

    @Schema(description = "매도 시 발생한 세금", example = "750")
    @PositiveOrZero
    private BigDecimal tax;

    @Schema(description = "매도일", example = "2026-04-15")
    private LocalDate sellDate;

    @Schema(description = "사용자 메모 / 비고", example = "장기 보유 후 매도")
    private String memo;
}
