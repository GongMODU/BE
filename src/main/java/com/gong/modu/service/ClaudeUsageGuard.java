package com.gong.modu.service;

import com.gong.modu.domain.entity.ApiUsageSummary;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ApiUsageSummaryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeUsageGuard {

    private final ApiUsageSummaryRepository usageSummaryRepository;

    @Value("${anthropic.credit-limit-usd:25.0}")
    private BigDecimal creditLimitUsd;

    @Value("${anthropic.price-per-million-input-tokens:3.0}")
    private BigDecimal pricePerMillionInputTokens;

    @Value("${anthropic.price-per-million-output-tokens:15.0}")
    private BigDecimal pricePerMillionOutputTokens;

    // 앱 시작 시 사용량 집계 행이 없으면 초기 행 생성
    @PostConstruct
    @Transactional
    public void init() {
        if (!usageSummaryRepository.existsById(1L)) {
            usageSummaryRepository.save(ApiUsageSummary.createInitial());
            log.info("[ClaudeUsageGuard] API 사용량 집계 행 초기화 완료");
        }
    }

    // Claude API 호출 전 크레딧 한도 초과 여부 확인
    @Transactional(readOnly = true)
    public void checkLimit() {
        ApiUsageSummary summary = usageSummaryRepository.findById(1L)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));

        if (summary.getEstimatedCostUsd().compareTo(creditLimitUsd) >= 0) {
            log.warn("[ClaudeUsageGuard] 크레딧 한도 초과 - 현재: ${}  / 한도: ${}",
                    summary.getEstimatedCostUsd(), creditLimitUsd);
            throw new CustomException(ErrorCode.CLAUDE_CREDIT_LIMIT_EXCEEDED);
        }
    }

    // 재결제 후 사용량 초기화
    @Transactional
    public BigDecimal resetUsage() {
        ApiUsageSummary summary = usageSummaryRepository.findForUpdate()
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));

        BigDecimal previousCost = summary.getEstimatedCostUsd();
        summary.resetUsage();

        log.info("[ClaudeUsageGuard] 사용량 초기화 완료 - 이전 누적 비용: ${}", previousCost);
        return previousCost;
    }

    // Claude API 호출 후 토큰 사용량 누적 기록
    @Transactional
    public void recordUsage(long inputTokens, long outputTokens) {
        ApiUsageSummary summary = usageSummaryRepository.findForUpdate()
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));

        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                .multiply(pricePerMillionInputTokens);

        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                .multiply(pricePerMillionOutputTokens);

        BigDecimal callCost = inputCost.add(outputCost);

        summary.addUsage(inputTokens, outputTokens, callCost);

        log.info("[ClaudeUsageGuard] 토큰 기록 - input: {}, output: {}, 이번 호출 비용: ${}, 누적: ${}",
                inputTokens, outputTokens,
                callCost.setScale(6, RoundingMode.HALF_UP),
                summary.getEstimatedCostUsd().setScale(6, RoundingMode.HALF_UP));
    }
}
