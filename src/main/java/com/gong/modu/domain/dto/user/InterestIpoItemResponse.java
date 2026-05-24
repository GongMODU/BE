package com.gong.modu.domain.dto.user;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 관심 공모주 목록 응답 한 건을 표현하는 DTO
// 홈 화면 리스트와 유사하게 디데이/공모가/증권사 정보를 함께 제공
@Getter
@Builder
public class InterestIpoItemResponse {

    private Long interestId;
    private Long ipoEventId;
    private String companyName;
    private IpoEventStatus status;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private LocalDate listingDate;
    private Boolean listingDateEstimated;
    private LocalDate lockupExpiryDate;
    private Boolean lockupExpiryDateEstimated;
    private BigDecimal offerPriceMin;
    private BigDecimal offerPriceMax;
    private BigDecimal offerPrice;
    private List<String> brokerNames;
    private LocalDateTime interestedAt;
}
