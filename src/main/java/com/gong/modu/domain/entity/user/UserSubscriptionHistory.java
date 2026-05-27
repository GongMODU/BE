package com.gong.modu.domain.entity.user;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// 사용자의 공모주 청약 이력을 저장하는 엔티티
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_subscription_histories")
public class UserSubscriptionHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 청약 이력을 작성한 사용자
    // 한 사용자는 여러 청약 이력을 가질 수 있으므로 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 서비스에 등록된 공모 이벤트와 연결
    // 사용자가 직접 입력한 청약 이력은 서비스의 ipo_events와 연결되지 않을 수 있으므로 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ipo_event_id")
    private IpoEvent ipoEvent;

    // 직접 입력 기업명
    // ipo_event_id가 없는 직접 입력 이력에서 사용
    @Size(max = 200)
    @Column(name = "input_company_name", length = 200)
    private String inputCompanyName;

    // 직접 입력 종목명
    // ipo_event_id가 없는 직접 입력 이력에서 사용
    @Size(max = 200)
    @Column(name = "input_stock_name", length = 200)
    private String inputStockName;

    // 청약 이력 상태
    // ONGOING 또는 COMPLETED로 저장
    @Enumerated(EnumType.STRING)
    @Column(name = "record_status", length = 20, nullable = false)
    private SubscriptionRecordStatus recordStatus;

    // 사용자가 실제로 청약한 증권사명
    // 사용자의 개인 기록 성격이므로 broker_id가 아니라 문자열로 저장
    @Size(max = 100)
    @Column(name = "security_company", length = 100)
    private String securityCompany;

    // 사용자가 청약 신청한 주식 수
    @PositiveOrZero
    @Column(name = "subscribed_quantity")
    private Long subscribedQuantity;

    // 실제 배정받은 주식 수
    @PositiveOrZero
    @Column(name = "allocated_quantity")
    private Long allocatedQuantity;

    // 청약에 넣은 금액
    @PositiveOrZero
    @Column(name = "subscription_amount", precision = 18, scale = 2)
    private BigDecimal subscriptionAmount;

    // 청약 당시 공모가
    @PositiveOrZero
    @Column(name = "offer_price", precision = 15, scale = 2)
    private BigDecimal offerPrice;

    // 매도 단가
    // 아직 매도하지 않았으면 NULL
    @PositiveOrZero
    @Column(name = "sell_price", precision = 15, scale = 2)
    private BigDecimal sellPrice;

    // 청약 또는 매도 과정에서 발생한 수수료
    @PositiveOrZero
    @Column(name = "fee", precision = 15, scale = 2)
    private BigDecimal fee;

    // 세금
    @PositiveOrZero
    @Column(name = "tax", precision = 15, scale = 2)
    private BigDecimal tax;

    // 매도일
    // 아직 매도하지 않았으면 NULL
    @Column(name = "sell_date")
    private LocalDate sellDate;

    // 사용자가 남긴 메모
    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    // 서비스에 등록된 공모 이벤트와 이력을 연결하는 메서드
    // 사용자가 직접 입력한 이력을 나중에 실제 ipo_events 데이터와 매칭할 때 사용
    public void linkIpoEvent(IpoEvent ipoEvent) {
        this.ipoEvent = ipoEvent;
    }

    // 청약 진행 정보를 수정하는 메서드
    // 청약 수량, 청약 금액, 증권사 등을 사용자가 수정할 때 사용
    public void updateSubscriptionInfo(
            String securityCompany,
            Long subscribedQuantity,
            BigDecimal subscriptionAmount,
            BigDecimal offerPrice,
            String memo
    ) {
        this.securityCompany = securityCompany;
        this.subscribedQuantity = subscribedQuantity;
        this.subscriptionAmount = subscriptionAmount;
        this.offerPrice = offerPrice;
        this.memo = memo;
    }

    // 배정 결과를 입력하는 메서드
    // 청약 결과 발표 후 실제 배정 수량을 반영할 때 사용
    public void updateAllocationResult(Long allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }

    // 매도 결과를 입력하고 청약 이력을 완료 상태로 변경하는 메서드
    // 매도 단가, 수수료, 세금, 매도일이 들어오면 COMPLETED로 상태를 변경
    public void completeRecord(
            BigDecimal sellPrice,
            BigDecimal fee,
            BigDecimal tax,
            LocalDate sellDate
    ) {
        this.sellPrice = sellPrice;
        this.fee = fee;
        this.tax = tax;
        this.sellDate = sellDate;
        this.recordStatus = SubscriptionRecordStatus.COMPLETED;
    }

    // 아직 매도 전이거나 기록을 다시 진행 중 상태로 돌려야 할 때 사용하는 메서드

    public void markOngoing() {
        this.recordStatus = SubscriptionRecordStatus.ONGOING;
    }

    // 사용자가 임의 필드를 편집할 때 사용하는 메서드 (null은 기존 값 유지)
    public void updateUserEditableFields(
            String inputStockName,
            String inputCompanyName,
            String securityCompany,
            Long subscribedQuantity,
            Long allocatedQuantity,
            BigDecimal offerPrice,
            BigDecimal subscriptionAmount,
            BigDecimal sellPrice,
            BigDecimal fee,
            BigDecimal tax,
            LocalDate sellDate,
            String memo
    ) {
        if (inputStockName != null) this.inputStockName = inputStockName;
        if (inputCompanyName != null) this.inputCompanyName = inputCompanyName;
        if (securityCompany != null) this.securityCompany = securityCompany;
        if (subscribedQuantity != null) this.subscribedQuantity = subscribedQuantity;
        if (allocatedQuantity != null) this.allocatedQuantity = allocatedQuantity;
        if (offerPrice != null) this.offerPrice = offerPrice;
        if (subscriptionAmount != null) this.subscriptionAmount = subscriptionAmount;
        if (sellPrice != null) this.sellPrice = sellPrice;
        if (fee != null) this.fee = fee;
        if (tax != null) this.tax = tax;
        if (sellDate != null) this.sellDate = sellDate;
        if (memo != null) this.memo = memo;
    }
}
