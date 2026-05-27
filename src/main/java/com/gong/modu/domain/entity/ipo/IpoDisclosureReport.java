package com.gong.modu.domain.entity.ipo;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.enums.ipo.DisclosureDocumentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

// 공시 원문과 구조화된 요약 결과를 저장하는 엔티티
// 공모주 상세 화면에서 공시 리포트 요약 컨텐츠를 제공하기 위해 사용
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "ipo_disclosure_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_ipo_disclosure_reports_event_rcept",
                        columnNames = {"ipo_event_id", "rcept_no"}
                )
        }
)
public class IpoDisclosureReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 하나의 공모 이벤트에는 증권신고서, 정정 신고서등의 여러 공시가 있을 수 있으므로 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ipo_event_id", nullable = false)
    private IpoEvent ipoEvent;

    // DART 공시 접수번호
    @NotBlank
    @Size(max = 20)
    @Column(name = "rcept_no", length = 20, nullable = false)
    private String rceptNo;

    // 공시명 (증권신고서, 정정신고서, 투자설명서 등)
    @Size(max = 255)
    @Column(name = "report_name", length = 255)
    private String reportName;

    // 공시 문서 성격
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50, nullable = false)
    @Builder.Default
    private DisclosureDocumentType documentType = DisclosureDocumentType.UNKNOWN;

    // 공시 원문 ZIP에서 추출한 텍스트
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    // 기업 요약 (기업 유형, 설립일, 상장일, 주요 목적 등)
    @Column(name = "company_summary", columnDefinition = "TEXT")
    private String companySummary;

    // 재무제표 요약
    @Column(name = "financial_summary", columnDefinition = "TEXT")
    private String financialSummary;

    // 투자자 보호 장치 요약
    @Column(name = "investor_protection_summary", columnDefinition = "TEXT")
    private String investorProtectionSummary;

    // 투자 포인트 요약 (SPAC: 합병 목표 산업, 합병 기한 등의 정보 담기 가능)
    @Column(name = "investment_point_summary", columnDefinition = "TEXT")
    private String investmentPointSummary;

    // 리스크 요약
    @Column(name = "risk_summary", columnDefinition = "TEXT")
    private String riskSummary;

    // 요약본 버전 (요약 규칙이 바뀔 수 있으므로 버전 필드 추가함)
    @Size(max = 50)
    @Column(name = "summary_version", length = 50)
    private String summaryVersion;

    // 원문 텍스트 갱신 메서드 (원문 파일을 다시 다운로드하거나 파싱 시 사용)
    public void updateOriginalText(String originalText) {
        this.originalText = originalText;
    }

    // 공시명, 문서 성격 갱신 메서드
    public void updateReportInfo(String reportName, DisclosureDocumentType documentType) {
        this.reportName = reportName;

        if (documentType != null) {
            this.documentType = documentType;
        }
    }

    // 문서 성격만 갱신하는 메서드 (ZIP 원문을 실제로 읽은 뒤 사용할 목적)
    public void updateDocumentType(DisclosureDocumentType documentType) {
        if (documentType != null) {
            this.documentType = documentType;
        }
    }

    // 구조화 요약 결과를 갱신하는 메서드
    // AI 요약 또는 규칙 기반 요약이 완료된 뒤 호출
    public void updateSummary(
            String companySummary,
            String financialSummary,
            String investorProtectionSummary,
            String investmentPointSummary,
            String riskSummary,
            String summaryVersion
    ) {
        this.companySummary = companySummary;
        this.financialSummary = financialSummary;
        this.investorProtectionSummary = investorProtectionSummary;
        this.investmentPointSummary = investmentPointSummary;
        this.riskSummary = riskSummary;
        this.summaryVersion = summaryVersion;
    }
}
