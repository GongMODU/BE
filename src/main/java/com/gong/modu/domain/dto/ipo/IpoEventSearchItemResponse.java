package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.MarketType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// IpoEvent 검색 결과 한 건을 표현하는 DTO
// 청약이력 4.2 현재 청약 중 이력 등록 시 종목 선택용 목록에서 사용
@Getter
@Builder
@Schema(description = "회사명 키워드 검색 결과 한 건. 청약이력 4.2 현재 청약 중 이력 등록 시 종목 선택용")
public class IpoEventSearchItemResponse {

    @Schema(description = "공모 이벤트 ID. 이 값을 청약이력 ongoing 등록 API의 ipoEventId 필드로 전달",
            example = "281")
    private Long ipoEventId;

    @Schema(description = "회사명", example = "피스피스스튜디오 주식회사")
    private String companyName;

    @Schema(description = "상장 대상 시장", example = "KOSDAQ",
            allowableValues = {"KOSPI", "KOSDAQ", "KONEX"})
    private MarketType marketType;

    @Schema(description = "공모 진행 상태", example = "UPCOMING",
            allowableValues = {"UPCOMING", "ONGOING", "CLOSED", "LISTED"})
    private IpoEventStatus status;

    @Schema(description = "청약 시작일", example = "2026-05-26")
    private LocalDate subscriptionStartDate;

    @Schema(description = "청약 종료일", example = "2026-05-27")
    private LocalDate subscriptionEndDate;

    @Schema(description = "상장일 (확정 또는 추정값)", example = "2026-06-03")
    private LocalDate listingDate;
}
