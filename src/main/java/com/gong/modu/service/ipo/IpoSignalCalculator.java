package com.gong.modu.service.ipo;

import com.gong.modu.domain.entity.ipo.IpoMetric;
import com.gong.modu.domain.enums.ipo.SignalLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 공모지표를 분석해 위험 점수(risk_score)와 신호등 등급(SignalLevel)을 계산하는 컴포넌트
// 기능명세서 3.1.2 핵심 지표 신호등 참고
@Component
public class IpoSignalCalculator {

    // 각 지표가 risk_score에 기여하는 가중치 (기능명세서 기준)
    private static final BigDecimal WEIGHT_INSTITUTIONAL = new BigDecimal("0.40"); // 기관경쟁률
    private static final BigDecimal WEIGHT_LOCKUP = new BigDecimal("0.30");        // 의무확약 비율
    private static final BigDecimal WEIGHT_CIRCULATING = new BigDecimal("0.20");   // 유통가능물량
    private static final BigDecimal WEIGHT_GENERAL = new BigDecimal("0.10");       // 일반청약 경쟁률

    // 신호등 등급 경계 점수 (risk_score >= 70 안정, 40~69 보통, 그 미만 위험)
    private static final BigDecimal GREEN_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal YELLOW_THRESHOLD = new BigDecimal("40");

    // 위험 점수와 신호등 등급을 함께 담는 결과 타입
    public record SignalResult(BigDecimal riskScore, SignalLevel signalLevel) {
    }

    // 공모지표로부터 위험 점수와 신호등 등급을 계산하는 메서드
    // 일부 지표가 없으면 해당 가중치를 제외하고 남은 지표만으로 정규화하여 계산
    public SignalResult calculate(IpoMetric metric) {
        if (metric == null) {
            return new SignalResult(null, null);
        }

        BigDecimal weightedSum = BigDecimal.ZERO; // 가중 점수 합
        BigDecimal weightTotal = BigDecimal.ZERO; // 실제 반영된 가중치 합

        // 기관경쟁률: 높을수록 안정
        if (metric.getInstitutionalCompetitionRate() != null) {
            int score = scoreInstitutionalRate(metric.getInstitutionalCompetitionRate());
            weightedSum = weightedSum.add(WEIGHT_INSTITUTIONAL.multiply(BigDecimal.valueOf(score)));
            weightTotal = weightTotal.add(WEIGHT_INSTITUTIONAL);
        }

        // 의무확약 비율: 높을수록 안정
        if (metric.getLockupRatio() != null) {
            int score = scoreLockupRatio(metric.getLockupRatio());
            weightedSum = weightedSum.add(WEIGHT_LOCKUP.multiply(BigDecimal.valueOf(score)));
            weightTotal = weightTotal.add(WEIGHT_LOCKUP);
        }

        // 유통가능물량 비율: 낮을수록 안정
        if (metric.getCirculatingSharesRatio() != null) {
            int score = scoreCirculatingRatio(metric.getCirculatingSharesRatio());
            weightedSum = weightedSum.add(WEIGHT_CIRCULATING.multiply(BigDecimal.valueOf(score)));
            weightTotal = weightTotal.add(WEIGHT_CIRCULATING);
        }

        // 일반청약 경쟁률: 높을수록 안정
        if (metric.getGeneralSubscriptionRate() != null) {
            int score = scoreGeneralRate(metric.getGeneralSubscriptionRate());
            weightedSum = weightedSum.add(WEIGHT_GENERAL.multiply(BigDecimal.valueOf(score)));
            weightTotal = weightTotal.add(WEIGHT_GENERAL);
        }

        // 반영된 지표가 하나도 없으면 신호등을 계산할 수 없음
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return new SignalResult(null, null);
        }

        // 누락 지표가 있어도 0~100 범위가 유지되도록 반영된 가중치 합으로 정규화
        BigDecimal riskScore = weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);

        return new SignalResult(riskScore, toSignalLevel(riskScore));
    }

    // 위험 점수를 신호등 등급으로 변환하는 메서드
    private SignalLevel toSignalLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(GREEN_THRESHOLD) >= 0) {
            return SignalLevel.GREEN;
        }
        if (riskScore.compareTo(YELLOW_THRESHOLD) >= 0) {
            return SignalLevel.YELLOW;
        }
        return SignalLevel.RED;
    }

    // 기관경쟁률을 0~100 점수로 환산하는 메서드
    private int scoreInstitutionalRate(BigDecimal rate) {
        double value = rate.doubleValue();
        if (value >= 1000) return 100;
        if (value >= 500) return 80;
        if (value >= 200) return 60;
        if (value >= 100) return 40;
        if (value >= 50) return 20;
        return 0;
    }

    // 의무확약 비율(0~1)을 0~100 점수로 환산하는 메서드 (높을수록 안정)
    private int scoreLockupRatio(BigDecimal ratio) {
        double value = ratio.doubleValue();
        if (value >= 0.50) return 100;
        if (value >= 0.30) return 75;
        if (value >= 0.15) return 50;
        if (value >= 0.05) return 25;
        return 0;
    }

    // 유통가능물량 비율(0~1)을 0~100 점수로 환산하는 메서드 (낮을수록 안정 → 역방향)
    private int scoreCirculatingRatio(BigDecimal ratio) {
        double value = ratio.doubleValue();
        if (value <= 0.20) return 100;
        if (value <= 0.30) return 75;
        if (value <= 0.40) return 50;
        if (value <= 0.50) return 25;
        return 0;
    }

    // 일반청약 경쟁률을 0~100 점수로 환산하는 메서드 (높을수록 안정)
    private int scoreGeneralRate(BigDecimal rate) {
        double value = rate.doubleValue();
        if (value >= 1000) return 100;
        if (value >= 500) return 75;
        if (value >= 200) return 50;
        if (value >= 50) return 25;
        return 0;
    }
}
