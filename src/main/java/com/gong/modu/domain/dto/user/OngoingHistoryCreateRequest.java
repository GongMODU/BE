package com.gong.modu.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "4.2 현재 청약 중 이력 생성 요청 (DB 등록된 IpoEvent와 연결 필수, ONGOING 상태로 저장)")
public class OngoingHistoryCreateRequest {

    @Schema(description = "청약한 공모주 IpoEvent ID. /api/ipo/search 검색 결과의 ipoEventId 값을 사용",
            example = "281",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long ipoEventId;

    @Schema(description = "청약한 증권사", example = "NH투자증권")
    @Size(max = 100)
    private String securityCompany;

    @Schema(description = "청약 신청 주식 수", example = "50")
    @PositiveOrZero
    private Long subscribedQuantity;

    @Schema(description = "청약 시 공모가", example = "21500")
    @PositiveOrZero
    private BigDecimal offerPrice;

    @Schema(description = "청약에 투입한 금액", example = "1075000")
    @PositiveOrZero
    private BigDecimal subscriptionAmount;

    @Schema(description = "사용자 메모", example = "수요예측 결과 좋아서 청약")
    private String memo;
}
