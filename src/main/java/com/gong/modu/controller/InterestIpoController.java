package com.gong.modu.controller;

import com.gong.modu.domain.dto.user.InterestIpoItemResponse;
import com.gong.modu.service.user.InterestIpoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "InterestIpo", description = "관심 공모주(찜) 관리 - 기능명세서 5.1.2")
@RestController
@RequestMapping("/api/interest-ipos")
@RequiredArgsConstructor
public class InterestIpoController {

    private final InterestIpoService interestIpoService;

    @Operation(summary = "관심 공모주 등록")
    @PostMapping("/{ipoEventId}")
    public ResponseEntity<Map<String, Long>> addInterest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long ipoEventId
    ) {
        Long interestId = interestIpoService.addInterest(userId, ipoEventId);
        return ResponseEntity.ok(Map.of("interestId", interestId));
    }

    @Operation(summary = "관심 공모주 해제")
    @DeleteMapping("/{ipoEventId}")
    public ResponseEntity<Void> removeInterest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long ipoEventId
    ) {
        interestIpoService.removeInterest(userId, ipoEventId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내 관심 공모주 목록 조회")
    @GetMapping
    public ResponseEntity<List<InterestIpoItemResponse>> getMyInterests(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(interestIpoService.getMyInterests(userId));
    }
}
