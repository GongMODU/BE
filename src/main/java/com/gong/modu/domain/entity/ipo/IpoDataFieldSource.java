package com.gong.modu.domain.entity.ipo;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.enums.ipo.DataSourceProvider;
import com.gong.modu.domain.enums.ipo.DataSourceType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

// 공모주 데이터의 필드별 출처를 추적하는 엔티티
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "ipo_data_field_sources")
public class IpoDataFieldSource extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 하나의 공모 이벤트에는 여러 필드 출처 기록이 생길 수 있으므로 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ipo_event_id", nullable = false)
    private IpoEvent ipoEvent;

    // 값이 저장된 대상 테이블명 (예: ipo_metrics, ipo_offerings, ipo_events)
    @NotBlank
    @Size(max = 50)
    @Column(name = "target_table", length = 50, nullable = false)
    private String targetTable;

    // 값이 저장된 대상 컬럼명 (예: institutional_competition_rate, offer_price, listing_date)
    @NotBlank
    @Size(max = 50)
    @Column(name = "target_column", length = 50, nullable = false)
    private String targetColumn;

    // 값의 생성 방식 (API, PARSING, MANUAL, CALCULATED 중 하나)
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private DataSourceType sourceType;

    // 데이터 제공자 (DART, KIS, KRX, INTERNAL 중 하나로 저장)
    @Enumerated(EnumType.STRING)
    @Column(name = "source_provider", length = 20)
    private DataSourceProvider sourceProvider;

    // 출처를 추적할 수 있는 참조값 (예: DART 접수번호, API endpoint, 관리자 입력 작업 ID, 파싱 파일명 등)
    @Size(max = 255)
    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    // 출처 관련 보충 설명
    @Column(name = "source_note", columnDefinition = "TEXT")
    private String sourceNote;

    // 출처 정보를 수정하는 메서드
    public void updateSource(
            DataSourceType sourceType,
            DataSourceProvider sourceProvider,
            String sourceReference,
            String sourceNote
    ) {
        this.sourceType = sourceType;
        this.sourceProvider = sourceProvider;
        this.sourceReference = sourceReference;
        this.sourceNote = sourceNote;
    }
}
