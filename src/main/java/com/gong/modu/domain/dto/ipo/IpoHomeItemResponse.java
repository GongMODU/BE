package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import com.gong.modu.domain.enums.ipo.SignalLevel;
import com.gong.modu.domain.enums.ipo.SignalUnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 홈 화면 청약 일정 리스트의 공모주 한 건을 표현하는 응답 DTO
// 기능명세서 3.1.1 청약 일정 리스트업 / 3.1.2 핵심 지표 신호등 참고
@Getter
@Builder
@Schema(description = "홈 화면 청약 일정 리스트의 공모주 한 건")
public class IpoHomeItemResponse {

    @Schema(description = "공모 이벤트 ID (상세 화면 이동에 사용)", example = "281")
    private Long ipoEventId;

    @Schema(description = "회사명", example = "피스피스스튜디오 주식회사")
    private String companyName;

    @Schema(description = "공모 진행 상태", example = "UPCOMING",
            allowableValues = {"UPCOMING", "ONGOING", "CLOSED", "LISTED"})
    private IpoEventStatus status;

    @Schema(description = "디데이 라벨 (청약 필터일 때: D-5, 청약시작일, 청약중, 청약종료일, 종료 / 그 외 필터일 때: D-N, D-DAY, 종료)",
            example = "D-2")
    private String dDayLabel;

    @Schema(description = "청약 시작일 (일반 투자자 청약 개시 날짜)", example = "2026-05-26")
    private LocalDate subscriptionStartDate;

    @Schema(description = "청약 종료일 (일반 투자자 청약 마감 날짜)", example = "2026-05-27")
    private LocalDate subscriptionEndDate;

    @Schema(description = "상장일. 공시에서 확정값을 추출하면 그 값을, 없으면 청약종료일 + 7일로 추정한 값",
            example = "2026-06-03")
    private LocalDate listingDate;

    @Schema(description = "listingDate가 추정값인지 여부. true면 청약종료일 기준 추정값 (실제 상장일은 거래소 확정 후 갱신됨)",
            example = "true")
    private Boolean listingDateEstimated;

    @Schema(description = "락업(보호예수·의무보유) 해제일. AI가 공시에서 직접 추출했거나 listingDate + 락업기간(개월)로 계산된 값",
            example = "2026-12-03")
    private LocalDate lockupExpiryDate;

    @Schema(description = "lockupExpiryDate가 계산된 추정값인지 여부. true면 listingDate에 락업 개월수를 더한 추정값",
            example = "true")
    private Boolean lockupExpiryDateEstimated;

    @Schema(description = "희망 공모가 하단 (수요예측 전 표시용). 수요예측 후엔 offerPrice가 확정공모가로 채워짐",
            example = "19000")
    private BigDecimal offerPriceMin;

    @Schema(description = "희망 공모가 상단 (수요예측 전 표시용)", example = "21500")
    private BigDecimal offerPriceMax;

    @Schema(description = "확정 공모가 (수요예측 완료 후 채워짐). 청약 시작 이후 노출",
            example = "21500")
    private BigDecimal offerPrice;

    @Schema(description = "청약 가능 증권사 목록", example = "[\"NH투자증권\", \"미래에셋증권\"]")
    private List<String> brokerNames;

    @Schema(description = "핵심 지표 신호등 등급. 지표가 부족하면 null이고 signalUnavailableReason에 사유가 채워짐",
            example = "YELLOW",
            allowableValues = {"GREEN", "YELLOW", "RED"})
    private SignalLevel signalLevel;

    @Schema(description = "신호등 산출에 사용된 위험 점수 (0~100). 지표가 부족하면 null",
            example = "45.71")
    private BigDecimal riskScore;

    @Schema(description = "signalLevel이 null일 때 그 사유. signalLevel이 채워졌으면 null. " +
                          "SPAC=스팩이라 수요예측 없음, PRE_DEMAND_FORECAST=수요예측 전, INCOMPLETE_DATA=수요예측 후이지만 지표 추출 실패",
            example = "PRE_DEMAND_FORECAST",
            allowableValues = {"SPAC", "PRE_DEMAND_FORECAST", "INCOMPLETE_DATA"})
    private SignalUnavailableReason signalUnavailableReason;

    @Schema(description = "로그인 사용자의 관심 공모주(찜) 등록 여부. 비로그인 시 항상 false",
            example = "false")
    private boolean favorited;
}
