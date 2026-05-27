package com.gong.modu.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Claude API 누적 사용량을 추적하는 싱글톤 엔티티 (항상 id=1인 행 하나만 존재)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "api_usage_summary")
public class ApiUsageSummary {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "total_input_tokens", nullable = false)
    private Long totalInputTokens;

    @Column(name = "total_output_tokens", nullable = false)
    private Long totalOutputTokens;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public static ApiUsageSummary createInitial() {
        return ApiUsageSummary.builder()
                .id(1L)
                .totalInputTokens(0L)
                .totalOutputTokens(0L)
                .estimatedCostUsd(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public void addUsage(long inputTokens, long outputTokens, BigDecimal callCost) {
        this.totalInputTokens += inputTokens;
        this.totalOutputTokens += outputTokens;
        this.estimatedCostUsd = this.estimatedCostUsd.add(callCost);
        this.lastUpdated = LocalDateTime.now();
    }

    public void resetUsage() {
        this.totalInputTokens = 0L;
        this.totalOutputTokens = 0L;
        this.estimatedCostUsd = BigDecimal.ZERO;
        this.lastUpdated = LocalDateTime.now();
    }
}
