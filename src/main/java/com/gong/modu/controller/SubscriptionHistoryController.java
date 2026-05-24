package com.gong.modu.controller;

import com.gong.modu.domain.dto.user.*;
import com.gong.modu.domain.enums.ipo.SubscriptionRecordStatus;
import com.gong.modu.service.user.SubscriptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "SubscriptionHistory", description = "청약 이력 관리 (4.1 과거 투자 이력 + 4.2 현재 청약 중 이력)")
@RestController
@RequestMapping("/api/subscription-history")
@RequiredArgsConstructor
public class SubscriptionHistoryController {

    private final SubscriptionHistoryService historyService;

    @Operation(summary = "4.1 과거 투자 이력 생성 (DB 종목 조회 없이 사용자 직접 입력, COMPLETED)")
    @PostMapping("/completed")
    public ResponseEntity<Map<String, Long>> createCompleted(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CompletedHistoryCreateRequest request
    ) {
        Long id = historyService.createCompleted(userId, request);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @Operation(summary = "4.2 현재 청약 중 이력 생성 (IpoEvent 연결 필수, ONGOING)")
    @PostMapping("/ongoing")
    public ResponseEntity<Map<String, Long>> createOngoing(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid OngoingHistoryCreateRequest request
    ) {
        Long id = historyService.createOngoing(userId, request);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @Operation(summary = "내 청약 이력 조회 (status 미지정 시 전체)")
    @GetMapping
    public ResponseEntity<List<SubscriptionHistoryResponse>> getMyHistories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) SubscriptionRecordStatus status
    ) {
        return ResponseEntity.ok(historyService.getMyHistories(userId, status));
    }

    @Operation(
            summary = "청약이력 기반 평균 수익률 요약 (기능명세서 3.3)",
            description = """
                    매도 완료(COMPLETED) 청약이력을 매도일 기준 월별 그룹핑해 평균 수익률을 반환합니다.

                    응답 구성:
                    - monthlyReturnRates: 매도 이력이 있는 월만 포함, 매도일 오름차순. 꺾은선 그래프 데이터로 사용.
                    - currentMonthReturnRate / lastMonthReturnRate: 이번달·저번달 평균. 막대 비교 그래프 데이터로 사용.
                    - trend: INCREASED/DECREASED/UNCHANGED/NO_DATA. 프론트가 이 값으로 안내 메시지 분기.

                    수익률 계산: ((sellPrice - offerPrice) × allocatedQuantity - fee - tax) / (offerPrice × allocatedQuantity) × 100
                    필수 값(sellPrice/offerPrice/allocatedQuantity) 중 하나라도 없으면 해당 이력은 계산에서 제외됩니다.

                    months 파라미터: 1~24 범위, 기본 6. 범위 밖이면 400 응답.
                    """
    )
    @GetMapping("/return-rate")
    public ResponseEntity<ReturnRateSummaryResponse> getReturnRateSummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "6") int months
    ) {
        return ResponseEntity.ok(historyService.getReturnRateSummary(userId, months));
    }

    @Operation(summary = "청약 이력 단건 조회")
    @GetMapping("/{historyId}")
    public ResponseEntity<SubscriptionHistoryResponse> getHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId
    ) {
        return ResponseEntity.ok(historyService.getHistory(userId, historyId));
    }

    @Operation(summary = "청약 이력 수정 (null 필드는 기존 값 유지)")
    @PatchMapping("/{historyId}")
    public ResponseEntity<Void> updateHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId,
            @RequestBody @Valid SubscriptionHistoryUpdateRequest request
    ) {
        historyService.updateHistory(userId, historyId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "ONGOING 이력을 매도 정보 입력으로 COMPLETED 전환")
    @PostMapping("/{historyId}/complete")
    public ResponseEntity<Void> completeHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId,
            @RequestBody @Valid CompleteHistoryRequest request
    ) {
        historyService.completeHistory(userId, historyId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "청약 이력 삭제")
    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId
    ) {
        historyService.deleteHistory(userId, historyId);
        return ResponseEntity.ok().build();
    }
}
