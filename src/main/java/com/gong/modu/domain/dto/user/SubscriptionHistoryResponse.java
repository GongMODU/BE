package com.gong.modu.domain.dto.user;

import com.gong.modu.domain.entity.user.UserSubscriptionHistory;
import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 청약 이력 단건 응답 DTO
@Getter
@Builder
@Schema(description = "청약 이력 단건 응답. 4.1 과거 투자 이력 + 4.2 현재 청약 중 이력 공용")
public class SubscriptionHistoryResponse {

    @Schema(description = "청약 이력 ID", example = "5")
    private Long id;

    @Schema(description = "청약 이력 상태. ONGOING=청약 중·매도 전, COMPLETED=매도 완료",
            example = "ONGOING",
            allowableValues = {"ONGOING", "COMPLETED"})
    private SubscriptionRecordStatus recordStatus;

    @Schema(description = "연결된 공모 이벤트 ID. 4.2 현재 청약 중 이력은 필수, 4.1 과거 입력 이력은 null",
            example = "281")
    private Long ipoEventId;

    @Schema(description = "연결된 공모 이벤트의 회사명 (DB 조인 결과). ipoEvent 없는 직접 입력 이력은 null",
            example = "피스피스스튜디오 주식회사")
    private String ipoEventCompanyName;

    @Schema(description = "사용자 직접 입력 종목명 (4.1 과거 입력 이력에서 사용)",
            example = "테스트종목")
    private String inputStockName;

    @Schema(description = "사용자 직접 입력 회사명 (4.1 과거 입력 이력에서 사용)",
            example = "테스트회사")
    private String inputCompanyName;

    @Schema(description = "사용자가 실제로 청약한 증권사명", example = "NH투자증권")
    private String securityCompany;

    @Schema(description = "청약 신청 주식 수", example = "100")
    private Long subscribedQuantity;

    @Schema(description = "실제 배정받은 주식 수", example = "10")
    private Long allocatedQuantity;

    @Schema(description = "청약 시 공모가", example = "21500")
    private BigDecimal offerPrice;

    @Schema(description = "청약에 투입한 금액", example = "1075000")
    private BigDecimal subscriptionAmount;

    @Schema(description = "매도 단가 (아직 매도 전이면 null)", example = "26000")
    private BigDecimal sellPrice;

    @Schema(description = "청약 또는 매도 수수료", example = "500")
    private BigDecimal fee;

    @Schema(description = "매도 시 발생한 세금", example = "750")
    private BigDecimal tax;

    @Schema(description = "매도일 (아직 매도 전이면 null)", example = "2026-06-15")
    private LocalDate sellDate;

    @Schema(description = "사용자 메모 / 비고", example = "장기 보유 예정")
    private String memo;

    @Schema(description = "이력 생성 시각", example = "2026-05-24T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "이력 마지막 수정 시각", example = "2026-05-24T10:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "연결된 공모주 찜 여부. ipoEventId 가 null 인 직접 입력 이력은 항상 false. " +
            "IpoHomeItemResponse / IpoDetailResponse 의 favorited 필드와 동일 시맨틱.",
            example = "true")
    private boolean favorited;

    // favorited 는 Service 에서 InterestIpoService 를 통해 별도 계산한 뒤 주입.
    // IpoEvent 가 null 인 4.1 직접 입력 이력은 호출 측에서 false 를 전달.
    public static SubscriptionHistoryResponse from(UserSubscriptionHistory history, boolean favorited) {
        return SubscriptionHistoryResponse.builder()
                .id(history.getId())
                .recordStatus(history.getRecordStatus())
                .ipoEventId(history.getIpoEvent() == null ? null : history.getIpoEvent().getId())
                .ipoEventCompanyName(history.getIpoEvent() == null
                        ? null
                        : history.getIpoEvent().getCompany().getCorpName())
                .inputStockName(history.getInputStockName())
                .inputCompanyName(history.getInputCompanyName())
                .securityCompany(history.getSecurityCompany())
                .subscribedQuantity(history.getSubscribedQuantity())
                .allocatedQuantity(history.getAllocatedQuantity())
                .offerPrice(history.getOfferPrice())
                .subscriptionAmount(history.getSubscriptionAmount())
                .sellPrice(history.getSellPrice())
                .fee(history.getFee())
                .tax(history.getTax())
                .sellDate(history.getSellDate())
                .memo(history.getMemo())
                .createdAt(history.getCreatedAt())
                .updatedAt(history.getUpdatedAt())
                .favorited(favorited)
                .build();
    }
}
