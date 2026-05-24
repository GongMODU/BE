package com.gong.modu.domain.dto.user;

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
public class CompleteHistoryRequest {

    // 매도 단가 (필수)
    @NotNull
    @PositiveOrZero
    private BigDecimal sellPrice;

    @PositiveOrZero
    private BigDecimal fee;

    @PositiveOrZero
    private BigDecimal tax;

    // 매도일 (필수)
    @NotNull
    private LocalDate sellDate;
}
