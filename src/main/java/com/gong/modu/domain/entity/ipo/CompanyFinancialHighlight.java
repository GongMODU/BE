package com.gong.modu.domain.entity.ipo;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.enums.ipo.FinancialStatementType;
import com.gong.modu.domain.enums.ipo.ReportCode;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

// 기업 재무 하이라이트를 저장하는 엔티티
// 기업 탭에서 매출액, 영업이익, 순이익, 자산, 부채, 자본 등을 보여주기 위해 사용
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "company_financial_highlights",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_company_financial_highlights",
                        columnNames = {"company_id", "bsns_year", "reprt_code", "fs_div"}
                )
        }
)
public class CompanyFinancialHighlight extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 재무 정보가 어느 기업의 것인지를 나타내는 외래키
    // 한 기업은 여러 연도/분기의 재무 정보를 가질 수 있으므로 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 사업 연도 (DART 재무제표 API에서 bsns_year로 사용하는 값)
    @NotBlank
    @Column(name = "bsns_year", length = 4, nullable = false)
    private String bsnsYear;

    // 보고서 코드
    @Enumerated(EnumType.STRING)
    @Column(name = "reprt_code", length = 20, nullable = false)
    private ReportCode reportCode;

    // 연결/개별 재무제표 구분 (CFS/OFS)
    @Enumerated(EnumType.STRING)
    @Column(name = "fs_div", length = 10, nullable = false)
    private FinancialStatementType financialStatementType;

    // 매출액 (SPAC의 경우 매출액이 0일 수 있으므로 NULL과 0을 구분할 수 있게 둠)
    @Column(name = "revenue")
    private Long revenue;

    // 영업이익
    // 손실이면 음수로 들어갈 수 있으므로 Positive 검증 X
    @Column(name = "operating_profit")
    private Long operatingProfit;

    // 당기순이익
    // 순손실이면 음수로 들어갈 수 있으므로 Positive 검증 X
    @Column(name = "net_income")
    private Long netIncome;

    // 자산총계
    @Column(name = "total_assets")
    private Long totalAssets;

    // 부채총계
    @Column(name = "total_liabilities")
    private Long totalLiabilities;

    // 자본총계
    // 자본잠식 등 상황에 따라 음수가 될 가능성도 있으므로 Positive 검증 X
    @Column(name = "total_equity")
    private Long totalEquity;

    // 통화 단위
    // 예: KRW
    @Column(name = "currency", length = 10)
    private String currency;

    // 재무 하이라이트 값을 갱신하는 메서드
    // DART 재무제표 API 재수집 결과를 반영할 때 사용
    public void updateFinancials(
            Long revenue,
            Long operatingProfit,
            Long netIncome,
            Long totalAssets,
            Long totalLiabilities,
            Long totalEquity,
            String currency
    ) {
        this.revenue = revenue;
        this.operatingProfit = operatingProfit;
        this.netIncome = netIncome;
        this.totalAssets = totalAssets;
        this.totalLiabilities = totalLiabilities;
        this.totalEquity = totalEquity;
        this.currency = currency;
    }
}
