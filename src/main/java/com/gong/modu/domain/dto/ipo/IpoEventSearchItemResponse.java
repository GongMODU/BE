package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.MarketType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// IpoEvent 검색 결과 한 건을 표현하는 DTO
// 청약이력 4.2 현재 청약 중 이력 등록 시 종목 선택용 목록에서 사용
@Getter
@Builder
public class IpoEventSearchItemResponse {

    private Long ipoEventId;
    private String companyName;
    private MarketType marketType;
    private IpoEventStatus status;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private LocalDate listingDate;
}
