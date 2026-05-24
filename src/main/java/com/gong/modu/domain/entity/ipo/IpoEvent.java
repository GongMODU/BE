package com.gong.modu.domain.entity.ipo;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.IpoEventType;
import com.gong.modu.domain.enums.ipo.MarketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

// 공모주 일정과 이벤트의 중심 엔티티
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "ipo_events")
public class IpoEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 여러 공모 이벤트가 하나의 기업에 속할 수 있으므로 다대일 관계
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // 공모 이벤트의 대상 기업

    @Size(max = 20)
    @Column(name = "rcept_no", length = 20)
    private String rceptNo; // DART 공시 접수번호

    @Size(max = 255)
    @Column(name = "event_name", length = 255)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false)
    private IpoEventType eventType; // IPO, 일반공모, 유상증자 등 이벤트 유형

    @Column(name = "demand_forecast_start")
    private LocalDate demandForecastStart; // 기관투자자 수요예측 시작일

    @Column(name = "demand_forecast_end")
    private LocalDate demandForecastEnd; // 기관투자자 수요예측 종료일

    @Column(name = "subscription_start_date")
    private LocalDate subscriptionStartDate; // 일반 투자자 청약 시작일

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate; // 일반 투자자 청약 종료일

    @Column(name = "payment_date")
    private LocalDate paymentDate; // 청약 대금 납입일

    @Column(name = "refund_date")
    private LocalDate refundDate; // 미배정 청약증거금 환불일

    @Column(name = "allocation_date")
    private LocalDate allocationDate; // 주식 배정 결과가 확정되는 날짜

    @Column(name = "listing_date")
    private LocalDate listingDate; // 상장일

    @Column(name = "lockup_expiry_date")
    private LocalDate lockupExpiryDate; // 락업해제일

    // 상장일이 거래소 승인 전이라 청약종료일 기준으로 추정해 채운 값인지 여부 (true = 추정값, false 또는 null = 확정값)
    @Column(name = "listing_date_estimated")
    private Boolean listingDateEstimated;

    // 락업해제일이 listingDate + lockupPeriodMonths 로 계산된 값인지 여부 (true = 추정값, false 또는 null = AI 직접 추출 확정값)
    @Column(name = "lockup_expiry_date_estimated")
    private Boolean lockupExpiryDateEstimated;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", length = 20) // 시장 구분: KOSPI, KOSDAQ, KONEX
    private MarketType marketType; // 이 공모 이벤트가 목표로 하는 상장 시장

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IpoEventStatus status; // 공모 이벤트의 현재 진행 상태

    @Size(max = 20)
    @Column(name = "main_report_rcept_no", length = 20)
    private String mainReportRceptNo; // 대표 공시 접수번호 (대표 증권신고서 접수번호)

    @OneToOne(mappedBy = "ipoEvent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true) // 공모 조건은 이벤트에 종속되는 일대일 관계
    private IpoOffering offering; // 공모가, 공모주식수, 배정수량 등 공모 조건 정보

    @OneToOne(mappedBy = "ipoEvent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true) // 지표도 이벤트에 종속되는 일대일 관계
    private IpoMetric metric; // 경쟁률, 확약비율, 신호등 등 분석 지표

    public void setOffering(IpoOffering offering) { // 양방향 일대일 관계를 안전하게 연결하기 위한 메서드
        this.offering = offering;
        if (offering != null) {
            offering.assignIpoEvent(this);
        }
    }

    public void setMetric(IpoMetric metric) { // 양방향 일대일 관계를 안전하게 연결하기 위한 메서드
        this.metric = metric; // 현재 이벤트가 지표 정보를 참조합니다.

        if (metric != null) {
            metric.assignIpoEvent(this);
        }
    }

    public void updateSchedule( // 일정 갱신 메서드
            LocalDate demandForecastStart, // 새 수요예측 시작일입
            LocalDate demandForecastEnd, // 새 수요예측 종료일
            LocalDate subscriptionStartDate, // 새 청약 시작일
            LocalDate subscriptionEndDate, // 새 청약 종료일
            LocalDate paymentDate, // 새 납입일
            LocalDate refundDate, // 새 환불일
            LocalDate allocationDate, // 새 배정일
            LocalDate listingDate, // 새 상장일
            LocalDate lockupExpiryDate // 새 보호예수 해제일
    ) {
        this.demandForecastStart = demandForecastStart;
        this.demandForecastEnd = demandForecastEnd;
        this.subscriptionStartDate = subscriptionStartDate;
        this.subscriptionEndDate = subscriptionEndDate;
        this.paymentDate = paymentDate;
        this.refundDate = refundDate;
        this.allocationDate = allocationDate;
        this.listingDate = listingDate;
        this.lockupExpiryDate = lockupExpiryDate;
    }

    // DART 주요정보 API에서 얻은 일정을 선택적으로 갱신하는 메서드
    // estkRs는 항목별로 빈 값을 줄 수 있으므로, 값이 있을 때만 갱신해 기존 값을 보존한다
    public void updateDartSchedule(
            LocalDate subscriptionStartDate,
            LocalDate subscriptionEndDate,
            LocalDate paymentDate,
            LocalDate allocationDate
    ) {
        if (subscriptionStartDate != null) {
            this.subscriptionStartDate = subscriptionStartDate;
        }
        if (subscriptionEndDate != null) {
            this.subscriptionEndDate = subscriptionEndDate;
        }
        if (paymentDate != null) {
            this.paymentDate = paymentDate;
        }
        if (allocationDate != null) {
            this.allocationDate = allocationDate;
        }
    }

    public void updateStatus(IpoEventStatus status) { // 공모이벤트 진행 상태 변경 메서드
        this.status = status; // UPCOMING, ONGOING, CLOSED, LISTED 중 하나로 갱신
    }

    // 청약/상장 일정과 기준일을 비교해 현재 진행 상태를 계산하는 메서드
    // status 컬럼은 시간이 지나면 낡으므로, 조회 시점 기준으로 상태를 다시 계산할 때 사용
    public IpoEventStatus resolveStatus(LocalDate today) {
        // 상장일이 있고 오늘 또는 과거라면 이미 상장된 상태
        if (listingDate != null && !listingDate.isAfter(today)) {
            return IpoEventStatus.LISTED;
        }

        // 오늘이 청약 시작일과 종료일 사이면 청약 진행중
        if (subscriptionStartDate != null && subscriptionEndDate != null
                && !today.isBefore(subscriptionStartDate)
                && !today.isAfter(subscriptionEndDate)) {
            return IpoEventStatus.ONGOING;
        }

        // 청약 종료일이 지났으면 청약 마감 (상장 전)
        if (subscriptionEndDate != null && today.isAfter(subscriptionEndDate)) {
            return IpoEventStatus.CLOSED;
        }

        // 위 조건에 모두 해당하지 않으면 청약 예정 상태
        return IpoEventStatus.UPCOMING;
    }

    // 공모 이벤트 기본 정보 갱신 메서드
    public void updateBasicInfo(
            String rceptNo,
            String eventName,
            IpoEventType eventType,
            MarketType marketType,
            String mainReportRceptNo
    ) {
        this.rceptNo = rceptNo;
        this.eventName = eventName;
        this.eventType = eventType;
        this.marketType = marketType;
        this.mainReportRceptNo = mainReportRceptNo;
    }

    // 공시 원문 파싱으로 얻은 일정 값만 선택적으로 반영하는 메서드
    public void updateParsedSchedule(
            LocalDate demandForecastStart,
            LocalDate demandForecastEnd,
            LocalDate subscriptionStartDate,
            LocalDate subscriptionEndDate,
            LocalDate refundDate,
            LocalDate listingDate,
            LocalDate lockupExpiryDate
    ) {

        // 파싱된 수요예측 시작일이 있을 때만 기존 값을 갱신
        if (demandForecastStart != null) {
            this.demandForecastStart = demandForecastStart;
        }

        // 파싱된 수요예측 종료일이 있을 때만 기존 값을 갱신
        if (demandForecastEnd != null) {
            this.demandForecastEnd = demandForecastEnd;
        }

        // 파싱된 청약 시작일이 있을 때만 기존 값을 갱신
        if (subscriptionStartDate != null) {
            this.subscriptionStartDate = subscriptionStartDate;
        }

        // 파싱된 청약 종료일이 있을 때만 기존 값을 갱신
        if (subscriptionEndDate != null) {
            this.subscriptionEndDate = subscriptionEndDate;
        }

        // 파싱된 환불일이 있을 때만 기존 값을 갱신
        if (refundDate != null) {
            this.refundDate = refundDate;
        }

        // 파싱된 상장일이 있을 때만 기존 값을 갱신
        if (listingDate != null) {
            this.listingDate = listingDate;
        }

        // 파싱된 보호예수 해제일이 있을 때만 기존 값을 갱신
        if (lockupExpiryDate != null) {
            this.lockupExpiryDate = lockupExpiryDate;
        }
    }

    // 상장일 추정 여부 갱신 메서드 (null이 아닌 경우에만 변경)
    public void markListingDateEstimated(Boolean estimated) {
        if (estimated != null) {
            this.listingDateEstimated = estimated;
        }
    }

    // 락업해제일 추정 여부 갱신 메서드 (null이 아닌 경우에만 변경)
    public void markLockupExpiryDateEstimated(Boolean estimated) {
        if (estimated != null) {
            this.lockupExpiryDateEstimated = estimated;
        }
    }
}
