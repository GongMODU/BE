package com.gong.modu.service.user;

import com.gong.modu.domain.dto.user.ReturnRateSummaryResponse;
import com.gong.modu.domain.entity.user.UserSubscriptionHistory;
import com.gong.modu.domain.enums.ipo.ReturnRateTrend;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 청약이력 기반 평균 수익률 계산 컴포넌트 (기능명세서 3.3)
// 한 건당 수익률: ((sellPrice - offerPrice) * allocatedQuantity - fee - tax) / (offerPrice * allocatedQuantity) * 100
// 월별 평균: 매도일(sellDate) 기준 월별 그룹핑 후 단순 평균
@Component
public class ReturnRateCalculator {

    // 백분율 계산 시 사용하는 스케일 (소수점 둘째자리)
    private static final int PERCENTAGE_SCALE = 2;

    // 한 건의 청약이력으로부터 수익률(%)을 계산하는 메서드
    // 필수 값(sellPrice/offerPrice/allocatedQuantity) 누락이거나 0이면 null 반환
    public BigDecimal calculateReturnRate(UserSubscriptionHistory history) {
        BigDecimal offerPrice = history.getOfferPrice();
        BigDecimal sellPrice = history.getSellPrice();
        Long allocated = history.getAllocatedQuantity();

        if (offerPrice == null || sellPrice == null || allocated == null || allocated <= 0) {
            return null;
        }
        if (offerPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal quantity = BigDecimal.valueOf(allocated);
        BigDecimal subscriptionAmount = offerPrice.multiply(quantity);
        BigDecimal sellAmount = sellPrice.multiply(quantity);

        BigDecimal fee = history.getFee() != null ? history.getFee() : BigDecimal.ZERO;
        BigDecimal tax = history.getTax() != null ? history.getTax() : BigDecimal.ZERO;

        BigDecimal profit = sellAmount.subtract(subscriptionAmount).subtract(fee).subtract(tax);

        return profit
                .multiply(BigDecimal.valueOf(100))
                .divide(subscriptionAmount, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    // COMPLETED 이력 리스트 + 요청 기간을 받아 응답 DTO를 만드는 메서드
    // 매도일 기준 그룹핑 → 월별 평균 산출 → 이번달/저번달 비교 트렌드 판정
    public ReturnRateSummaryResponse summarize(
            List<UserSubscriptionHistory> completedHistories,
            int months,
            LocalDate today
    ) {
        // 1) 매도일 기준 YearMonth 별로 수익률 누적 (필수값 누락 이력은 자동 제외)
        Map<YearMonth, List<BigDecimal>> ratesByMonth = new LinkedHashMap<>();
        for (UserSubscriptionHistory h : completedHistories) {
            LocalDate sellDate = h.getSellDate();
            if (sellDate == null) continue;

            BigDecimal rate = calculateReturnRate(h);
            if (rate == null) continue;

            YearMonth ym = YearMonth.from(sellDate);
            ratesByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(rate);
        }

        // 2) 월 기준 오름차순으로 정렬한 응답 리스트 구성
        List<ReturnRateSummaryResponse.MonthlyReturnRate> monthly = ratesByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    YearMonth ym = entry.getKey();
                    List<BigDecimal> rates = entry.getValue();
                    return ReturnRateSummaryResponse.MonthlyReturnRate.builder()
                            .year(ym.getYear())
                            .month(ym.getMonthValue())
                            .averageReturnRate(average(rates))
                            .recordCount(rates.size())
                            .build();
                })
                .toList();

        // 3) 이번달/저번달 평균 + 트렌드 판정
        YearMonth currentYm = YearMonth.from(today);
        YearMonth lastYm = currentYm.minusMonths(1);

        BigDecimal currentMonthAvg = ratesByMonth.containsKey(currentYm)
                ? average(ratesByMonth.get(currentYm))
                : null;
        BigDecimal lastMonthAvg = ratesByMonth.containsKey(lastYm)
                ? average(ratesByMonth.get(lastYm))
                : null;

        ReturnRateTrend trend = resolveTrend(currentMonthAvg, lastMonthAvg);

        return ReturnRateSummaryResponse.builder()
                .months(months)
                .monthlyReturnRates(monthly)
                .currentMonthReturnRate(currentMonthAvg)
                .lastMonthReturnRate(lastMonthAvg)
                .trend(trend)
                .build();
    }

    // 수익률 리스트의 단순 평균을 계산하는 메서드 (스케일 2, HALF_UP)
    private BigDecimal average(List<BigDecimal> rates) {
        BigDecimal sum = rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rates.size()), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    // 이번달/저번달 평균을 비교해 트렌드 enum을 결정하는 메서드
    private ReturnRateTrend resolveTrend(BigDecimal currentAvg, BigDecimal lastAvg) {
        if (currentAvg == null || lastAvg == null) {
            return ReturnRateTrend.NO_DATA;
        }
        int cmp = currentAvg.compareTo(lastAvg);
        if (cmp > 0) return ReturnRateTrend.INCREASED;
        if (cmp < 0) return ReturnRateTrend.DECREASED;
        return ReturnRateTrend.UNCHANGED;
    }
}
