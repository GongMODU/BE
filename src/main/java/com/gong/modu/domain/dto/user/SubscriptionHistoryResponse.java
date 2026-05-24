package com.gong.modu.domain.dto.user;

import com.gong.modu.domain.entity.user.UserSubscriptionHistory;
import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 청약 이력 단건 응답 DTO
@Getter
@Builder
public class SubscriptionHistoryResponse {

    private Long id;
    private SubscriptionRecordStatus recordStatus;

    // 연결된 IpoEvent 정보 (없으면 null)
    private Long ipoEventId;
    private String ipoEventCompanyName;

    // 사용자 직접 입력 종목명·회사명
    private String inputStockName;
    private String inputCompanyName;

    private String securityCompany;
    private Long subscribedQuantity;
    private Long allocatedQuantity;
    private BigDecimal offerPrice;
    private BigDecimal subscriptionAmount;
    private BigDecimal sellPrice;
    private BigDecimal fee;
    private BigDecimal tax;
    private LocalDate sellDate;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SubscriptionHistoryResponse from(UserSubscriptionHistory history) {
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
                .build();
    }
}
