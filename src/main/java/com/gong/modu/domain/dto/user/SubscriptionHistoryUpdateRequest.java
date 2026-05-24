package com.gong.modu.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// 청약 이력 수정 요청 (모든 필드 선택, null이 아닌 값만 갱신)
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "청약 이력 수정 요청. 모든 필드 선택값이며, null이 아닌 필드만 기존 값을 덮어씁니다 (PATCH 방식)")
public class SubscriptionHistoryUpdateRequest {

    @Schema(description = "종목명 (직접 입력 이력의 경우)", example = "수정된종목명")
    @Size(max = 200)
    private String inputStockName;

    @Schema(description = "회사명 (직접 입력 이력의 경우)", example = "수정된회사명")
    @Size(max = 200)
    private String inputCompanyName;

    @Schema(description = "청약 증권사", example = "KB증권")
    @Size(max = 100)
    private String securityCompany;

    @Schema(description = "청약 신청 주식 수", example = "100")
    @PositiveOrZero
    private Long subscribedQuantity;

    @Schema(description = "실제 배정받은 주식 수 (배정 결과 발표 후 입력)", example = "10")
    @PositiveOrZero
    private Long allocatedQuantity;

    @Schema(description = "공모가", example = "21500")
    @PositiveOrZero
    private BigDecimal offerPrice;

    @Schema(description = "청약에 투입한 금액", example = "1075000")
    @PositiveOrZero
    private BigDecimal subscriptionAmount;

    @Schema(description = "매도 단가", example = "26000")
    @PositiveOrZero
    private BigDecimal sellPrice;

    @Schema(description = "수수료", example = "500")
    @PositiveOrZero
    private BigDecimal fee;

    @Schema(description = "세금", example = "750")
    @PositiveOrZero
    private BigDecimal tax;

    @Schema(description = "매도일", example = "2026-06-15")
    private LocalDate sellDate;

    @Schema(description = "사용자 메모", example = "비고 수정")
    private String memo;
}
