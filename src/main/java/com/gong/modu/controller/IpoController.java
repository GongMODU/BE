package com.gong.modu.controller;

import com.gong.modu.domain.dto.ipo.IpoDisclosureReportResponse;
import com.gong.modu.domain.dto.ipo.IpoEventSearchItemResponse;
import com.gong.modu.domain.dto.ipo.IpoFinancialResponse;
import com.gong.modu.domain.dto.ipo.IpoFinancialStatusResponse;
import com.gong.modu.domain.dto.ipo.IpoHomeItemResponse;
import com.gong.modu.domain.enums.ipo.IpoScheduleFilter;
import com.gong.modu.service.ipo.IpoDisclosureReportQueryService;
import com.gong.modu.service.ipo.IpoFinancialQueryService;
import com.gong.modu.service.ipo.IpoHomeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Ipo", description = "공모주 일정/검색 (3.1.1 청약 일정 리스트업, 4.2 종목 검색)")
@RestController
@RequestMapping("/api/ipo")
@RequiredArgsConstructor
public class IpoController {

    private final IpoDisclosureReportQueryService queryService;
    private final IpoFinancialQueryService financialQueryService;
    private final IpoHomeQueryService homeQueryService;

    @Operation(
            summary = "홈 화면 청약 일정 리스트 조회",
            description = """
                    필터 기준 일정일이 최근 3개월 ~ 향후 4주 범위에 드는 공모주를 일정일 순으로 반환합니다.

                    필터 종류:
                    - `SUBSCRIPTION` (기본값) — 일반 투자자 청약일 기준
                    - `DEMAND_FORECAST` — 기관 수요예측일 기준
                    - `LOCKUP_EXPIRY` — 보호예수 해제일 기준
                    - `REFUND` — 환불일 기준
                    - `LISTING` — 상장일 기준
                    - `ALLOCATION` — 배정일 기준

                    응답 필드 참고:
                    - `listingDate` / `lockupExpiryDate`가 백엔드 추정값이면 `listingDateEstimated` / `lockupExpiryDateEstimated`가 `true`로 내려옵니다.
                    - `signalLevel`이 null이면 `signalUnavailableReason`에 사유(SPAC / PRE_DEMAND_FORECAST / INCOMPLETE_DATA)가 채워집니다.
                        - SPAC                    → 스팩이라 수요예측 자체 없음 (정상)
                        - PRE_DEMAND_FORECAST     → 수요예측 전 (지표 곧 채워질 예정)
                        - INCOMPLETE_DATA         → 수요예측 후인데 AI가 지표 추출 실패 (재파싱 필요)
                    - 로그인 사용자만 `favorited`가 정확히 반영되며, 비로그인은 모두 false.
                    """
    )
    @GetMapping("/home")
    public ResponseEntity<List<IpoHomeItemResponse>> getHomeSchedule(
            @RequestParam(defaultValue = "SUBSCRIPTION") IpoScheduleFilter filter,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(homeQueryService.getHomeSchedule(filter, userId));
    }

    @Operation(
            summary = "회사명 키워드로 공모주 검색",
            description = """
                    회사명에 키워드가 포함된 공모 이벤트를 최대 **20건**까지 최신순으로 반환합니다.

                    용도: 청약이력 4.2 "현재 청약 중 이력" 등록 시 사용자가 자신이 청약한 종목을 선택하는 검색창에서 사용합니다.
                    선택한 응답의 `ipoEventId`를 `POST /api/subscription-history/ongoing` 의 `ipoEventId` 필드로 넘기면 됩니다.

                    검색은 대소문자 구분 없이 부분 일치합니다. 빈 키워드는 빈 배열을 반환합니다.
                    """
    )
    @GetMapping("/search")
    public ResponseEntity<List<IpoEventSearchItemResponse>> searchIpoEvents(
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(homeQueryService.searchIpoEvents(keyword));
    }

    @GetMapping("/{ipoEventId}/disclosure")
    public ResponseEntity<IpoDisclosureReportResponse> getDisclosureReport(@PathVariable Long ipoEventId) {
        return ResponseEntity.ok(queryService.getDisclosureReport(ipoEventId));
    }

    // 재무 차트용 연간 재무 하이라이트 조회 / 최신 2개년, bsnsYear 오름차순
    // 데이터 없으면 빈 배열 반환 (404 아님)
    @GetMapping("/{ipoEventId}/financials")
    public ResponseEntity<List<IpoFinancialResponse>> getFinancials(@PathVariable Long ipoEventId) {
        return ResponseEntity.ok(financialQueryService.getFinancials(ipoEventId));
    }

    @GetMapping("/{ipoEventId}/financials/status")
    public ResponseEntity<IpoFinancialStatusResponse> getFinancialStatus(@PathVariable Long ipoEventId) {
        return ResponseEntity.ok(financialQueryService.getFinancialStatus(ipoEventId));
    }
}
