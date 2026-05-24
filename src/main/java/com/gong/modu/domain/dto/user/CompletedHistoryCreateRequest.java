package com.gong.modu.domain.dto.user;

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
public class CompletedHistoryCreateRequest {

    // 종목명 (필수, 직접 입력)
    @NotBlank
    @Size(max = 200)
    private String inputStockName;

    // 회사명 (선택, 직접 입력)
    @Size(max = 200)
    private String inputCompanyName;

    // 증권사 (선택)
    @Size(max = 100)
    private String securityCompany;

    // 청약 수량
    @PositiveOrZero
    private Long subscribedQuantity;

    // 배정 수량
    @PositiveOrZero
    private Long allocatedQuantity;

    // 매도가
    @PositiveOrZero
    private BigDecimal sellPrice;

    // 수수료
    @PositiveOrZero
    private BigDecimal fee;

    // 제세금
    @PositiveOrZero
    private BigDecimal tax;

    // 매도일
    private LocalDate sellDate;

    // 기타 비고
    private String memo;
}
