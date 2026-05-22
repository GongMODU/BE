package com.gong.modu.controller;

import com.gong.modu.domain.dto.ipo.IpoDisclosureReportResponse;
import com.gong.modu.domain.dto.ipo.IpoFinancialResponse;
import com.gong.modu.domain.dto.ipo.IpoHomeItemResponse;
import com.gong.modu.domain.enums.ipo.IpoScheduleFilter;
import com.gong.modu.service.ipo.IpoDisclosureReportQueryService;
import com.gong.modu.service.ipo.IpoFinancialQueryService;
import com.gong.modu.service.ipo.IpoHomeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ipo")
@RequiredArgsConstructor
public class IpoController {

    private final IpoDisclosureReportQueryService queryService;
    private final IpoFinancialQueryService financialQueryService;
    private final IpoHomeQueryService homeQueryService;

    // 홈 화면 청약 일정 리스트 조회 (필터 기준 일정일 최근 3개월~향후 4주)
    // filter: 수요예측/청약/락업해제/환불/상장/배정 (기본값 청약)
    @GetMapping("/home")
    public ResponseEntity<List<IpoHomeItemResponse>> getHomeSchedule(
            @RequestParam(defaultValue = "SUBSCRIPTION") IpoScheduleFilter filter
    ) {
        return ResponseEntity.ok(homeQueryService.getHomeSchedule(filter));
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
}
