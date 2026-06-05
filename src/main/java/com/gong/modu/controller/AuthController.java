package com.gong.modu.controller;

import com.gong.modu.domain.dto.user.*;
import com.gong.modu.service.user.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "로그인 / 회원가입")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 인증코드 발송")
    @PostMapping("/email/sendcode")
    public ResponseEntity<Void> sendEmailCode(@RequestBody @Valid EmailVerificationRequest request) {
        authService.sendEmailVerificationCode(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "이메일 인증코드 검증")
    @PostMapping("/email/verifycode")
    public ResponseEntity<Void> verifyEmailCode(@RequestBody @Valid EmailCodeVerifyRequest request) {
        authService.verifyEmailCode(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody @Valid LocalSignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LocalLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "액세스 토큰 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody @Valid TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal Long userId
    ) {
        String accessToken = authHeader.substring(7);
        authService.logout(accessToken, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원탈퇴")
    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal Long userId,
            @RequestBody WithdrawRequest request
    ) {
        String accessToken = authHeader.substring(7);
        authService.withdraw(userId, accessToken, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 재설정 링크 발송")
    @PostMapping("/password/resetrequest")
    public ResponseEntity<Void> sendPasswordResetLink(@RequestBody @Valid PasswordResetRequest request) {
        authService.sendPasswordResetLink(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 재설정")
    @PostMapping("/password/resetconfirm")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetConfirmRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
