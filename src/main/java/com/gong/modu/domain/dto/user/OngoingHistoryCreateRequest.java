package com.gong.modu.domain.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// 4.2 현재 청약 중 이력 입력 요청 (DB의 IpoEvent와 연결 필수)
// 등록 시 record_status = ONGOING 으로 저장
@Getter
@Setter
@NoArgsConstructor
public class OngoingHistoryCreateRequest {

    // 청약한 공모주 IpoEvent ID (필수, DB에 존재해야 함)
    @NotNull
    private Long ipoEventId;

    // 청약 증권사
    @Size(max = 100)
    private String securityCompany;

    // 청약 수량
    @PositiveOrZero
    private Long subscribedQuantity;

    // 청약 시 공모가
    @PositiveOrZero
    private BigDecimal offerPrice;

    // 청약에 투입한 금액
    @PositiveOrZero
    private BigDecimal subscriptionAmount;

    // 메모
    private String memo;
}
