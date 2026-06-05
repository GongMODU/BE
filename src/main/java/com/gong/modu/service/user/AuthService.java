package com.gong.modu.service.user;

import com.gong.modu.domain.dto.user.*;
import com.gong.modu.domain.entity.user.EmailVerificationCode;
import com.gong.modu.domain.entity.user.PasswordResetToken;
import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.enums.user.Provider;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.user.EmailVerificationCodeRepository;
import com.gong.modu.repository.user.PasswordResetTokenRepository;
import com.gong.modu.repository.user.UserInterestIpoRepository;
import com.gong.modu.repository.user.UserInvestmentProfileSessionRepository;
import com.gong.modu.repository.user.UserRepository;
import com.gong.modu.repository.user.UserSubscriptionHistoryRepository;
import com.gong.modu.util.JwtUtil;
import com.gong.modu.util.RedisUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserInterestIpoRepository userInterestIpoRepository;
    private final UserSubscriptionHistoryRepository userSubscriptionHistoryRepository;
    private final UserInvestmentProfileSessionRepository userInvestmentProfileSessionRepository;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${email.verification.expiration}")
    private long emailVerificationExpiration; // 초 단위 (300 = 5분)

    @Value("${password.reset.expiration:30}")
    private long passwordResetExpirationMinutes; // 분 단위 (기본값 30분)

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendEmailVerificationCode(EmailVerificationRequest request) {
        String email = request.getEmail();

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        redisUtil.saveEmailCode(email, code, emailVerificationExpiration);

        EmailVerificationCode entity = EmailVerificationCode.builder()
                .email(email)
                .code(code)
                .expiresAt(LocalDateTime.now().plusSeconds(emailVerificationExpiration))
                .build();
        emailVerificationCodeRepository.save(entity);

        emailService.sendVerificationCode(email, code);
    }

    // ────────── 이메일 인증코드 검증 ──────────
    @Transactional
    public void verifyEmailCode(EmailCodeVerifyRequest request) {
        String email = request.getEmail();
        String inputCode = request.getCode();

        String savedCode = redisUtil.getEmailCode(email)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPIRED_VERIFICATION_CODE));

        if (!savedCode.equals(inputCode)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        redisUtil.deleteEmailCode(email);
        redisUtil.markEmailVerified(email);

        emailVerificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .ifPresent(EmailVerificationCode::verify);
    }

    // 회원가입
    @Transactional
    public void signup(LocalSignupRequest request) {
        String email = request.getEmail();

        if (!redisUtil.isEmailVerified(email)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .provider(Provider.LOCAL)
                .build();
        userRepository.save(user);

        redisUtil.deleteEmailVerified(email);
    }

    // ────────── 로그인 ──────────
    @Transactional(readOnly = true)
    public AuthResponse login(LocalLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return issueTokens(user);
    }

    // ────────── 토큰 재발급 ──────────
    @Transactional(readOnly = true)
    public AuthResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        Claims claims = jwtUtil.parseClaims(refreshToken);

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = Long.parseLong(claims.getSubject());

        String storedToken = redisUtil.getRefreshToken(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
        if (!storedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return issueTokens(user);
    }

    // ────────── 로그아웃 ──────────
    public void logout(String accessToken, Long userId) {
        Claims claims = jwtUtil.parseClaims(accessToken);
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();

        if (remainingMillis > 0) {
            redisUtil.addToBlacklist(accessToken, remainingMillis);
        }

        redisUtil.deleteRefreshToken(userId);
    }

    // ────────── 비밀번호 재설정 링크 발송 ──────────
    @Transactional
    public void sendPasswordResetLink(PasswordResetRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() != Provider.LOCAL) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_NOT_SUPPORTED_FOR_PASSWORD_RESET);
        }

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(passwordResetExpirationMinutes))
                .build();
        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetLink(user.getEmail(), resetLink);
    }

    // ────────── 비밀번호 재설정 ──────────
    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new CustomException(ErrorCode.RESET_TOKEN_NOT_FOUND));

        if (resetToken.isUsed()) {
            throw new CustomException(ErrorCode.USED_RESET_TOKEN);
        }
        if (resetToken.isExpired()) {
            throw new CustomException(ErrorCode.EXPIRED_RESET_TOKEN);
        }

        resetToken.getUser().updatePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        resetToken.use();
    }

    // ────────── 회원탈퇴 ──────────
    @Transactional
    public void withdraw(Long userId, String accessToken, WithdrawRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() == Provider.LOCAL) {
            if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new CustomException(ErrorCode.INVALID_PASSWORD);
            }
        }

        userInvestmentProfileSessionRepository.deleteByUser(user);
        userInterestIpoRepository.deleteByUser(user);
        userSubscriptionHistoryRepository.deleteByUser(user);
        passwordResetTokenRepository.deleteByUser(user);
        userRepository.delete(user);

        Claims claims = jwtUtil.parseClaims(accessToken);
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            redisUtil.addToBlacklist(accessToken, remainingMillis);
        }
        redisUtil.deleteRefreshToken(userId);
    }

    // ────────── 공통: 토큰 발급 ──────────
    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        redisUtil.saveRefreshToken(user.getId(), refreshToken, jwtUtil.getRefreshTokenExpiration());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }
}
