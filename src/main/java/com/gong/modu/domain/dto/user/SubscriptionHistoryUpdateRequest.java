package com.gong.modu.domain.dto.user;

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
public class SubscriptionHistoryUpdateRequest {

    @Size(max = 200)
    private String inputStockName;

    @Size(max = 200)
    private String inputCompanyName;

    @Size(max = 100)
    private String securityCompany;

    @PositiveOrZero
    private Long subscribedQuantity;

    @PositiveOrZero
    private Long allocatedQuantity;

    @PositiveOrZero
    private BigDecimal offerPrice;

    @PositiveOrZero
    private BigDecimal subscriptionAmount;

    @PositiveOrZero
    private BigDecimal sellPrice;

    @PositiveOrZero
    private BigDecimal fee;

    @PositiveOrZero
    private BigDecimal tax;

    private LocalDate sellDate;

    private String memo;
}
