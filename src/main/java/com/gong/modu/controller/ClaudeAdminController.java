package com.gong.modu.controller;

import com.gong.modu.service.ClaudeUsageGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ClaudeAdminController {

    private final ClaudeUsageGuard claudeUsageGuard;

    // 재결제 후 Claude API 누적 사용량을 초기화하는 관리자용 API
    @PostMapping("/admin/claude/reset-usage")
    public Map<String, String> resetUsage() {
        BigDecimal previousCost = claudeUsageGuard.resetUsage();
        return Map.of(
                "message", "Claude 사용량 초기화 완료",
                "previousCostUsd", previousCost.toPlainString()
        );
    }
}
