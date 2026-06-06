package com.gong.modu.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// ONGOING 청약 이력을 COMPLETED 로 전환할 때 사용하는 매도 정보 입력 요청
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "ONGOING 상태의 청약 이력에 매도 정보를 입력해 COMPLETED 상태로 전환할 때 사용")
public class CompleteHistoryRequest {

    @Schema(description = "매도 단가 (필수)", example = "26000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @PositiveOrZero
    private BigDecimal sellPrice;

    @Schema(description = "매도 수수료", example = "100")
    @PositiveOrZero
    private BigDecimal fee;

    @Schema(description = "매도 시 발생한 세금", example = "50")
    @PositiveOrZero
    private BigDecimal tax;

    @Schema(description = "매도일 (필수)", example = "2026-06-20", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private LocalDate sellDate;

    // 옵션 필드. 매도 화면에서 배정수량을 함께 입력하는 흐름 지원용.
    // null 이면 기존값 유지 (PATCH 로 이미 입력한 케이스), 값이 있으면 함께 업데이트.
    @Schema(description = "실제 배정받은 주식 수 (옵션). 매도 화면에서 함께 입력받기 위한 필드. " +
            "null 이면 기존 값을 유지하고, 값이 들어오면 함께 업데이트합니다.",
            example = "10")
    @PositiveOrZero
    private Long allocatedQuantity;
}
