package com.gong.modu.domain.dto.user;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "관심 공모주(찜) 목록 한 건. 기능명세서 5.1.2 관심 공모주 참고")
public class InterestIpoItemResponse {

    @Schema(description = "관심 공모주 등록 ID (찜 해제 시 사용)", example = "12")
    private Long interestId;

    @Schema(description = "찜한 공모 이벤트 ID", example = "281")
    private Long ipoEventId;

    @Schema(description = "회사명", example = "피스피스스튜디오 주식회사")
    private String companyName;

    @Schema(description = "공모 진행 상태", example = "UPCOMING",
            allowableValues = {"UPCOMING", "ONGOING", "CLOSED", "LISTED"})
    private IpoEventStatus status;

    @Schema(description = "청약 시작일", example = "2026-05-26")
    private LocalDate subscriptionStartDate;

    @Schema(description = "청약 종료일", example = "2026-05-27")
    private LocalDate subscriptionEndDate;

    @Schema(description = "상장일 (확정 또는 청약종료일 + 7일 추정값)", example = "2026-06-03")
    private LocalDate listingDate;

    @Schema(description = "listingDate가 추정값인지 여부", example = "true")
    private Boolean listingDateEstimated;

    @Schema(description = "락업 해제일 (확정 또는 계산 추정값)", example = "2026-12-03")
    private LocalDate lockupExpiryDate;

    @Schema(description = "lockupExpiryDate가 계산 추정값인지 여부", example = "true")
    private Boolean lockupExpiryDateEstimated;

    @Schema(description = "희망 공모가 하단", example = "19000")
    private BigDecimal offerPriceMin;

    @Schema(description = "희망 공모가 상단", example = "21500")
    private BigDecimal offerPriceMax;

    @Schema(description = "확정 공모가 (수요예측 후 채워짐)", example = "21500")
    private BigDecimal offerPrice;

    @Schema(description = "청약 가능 증권사 목록", example = "[\"NH투자증권\", \"미래에셋증권\"]")
    private List<String> brokerNames;

    @Schema(description = "관심 공모주로 등록한 시각", example = "2026-05-20T14:30:00")
    private LocalDateTime interestedAt;
}
