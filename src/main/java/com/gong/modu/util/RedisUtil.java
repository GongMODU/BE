package com.gong.modu.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    private static final String EMAIL_CODE_PREFIX = "email:code:";
    private static final String EMAIL_VERIFIED_PREFIX = "email:verified:";

    private static final String DISCLOSURE_PARSE_LOCK_PREFIX = "lock:disclosure-parse:";
    private static final String DISCLOSURE_PARSE_FAILED_PREFIX = "failed:disclosure-parse:";

    // 이메일 인증코드 저장 (TTL: 초 단위)
    public void saveEmailCode(String email, String code, long ttlSeconds) {
        redisTemplate.opsForValue()
                .set(EMAIL_CODE_PREFIX + email, code, Duration.ofSeconds(ttlSeconds));
    }

    // 이메일 인증코드 조회
    public Optional<String> getEmailCode(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + email));
    }

    // 이메일 인증코드 삭제 (인증 완료 후 즉시 제거)
    public void deleteEmailCode(String email) {
        redisTemplate.delete(EMAIL_CODE_PREFIX + email);
    }

    // 이메일 인증 완료 마킹 저장
    public void markEmailVerified(String email) {
        redisTemplate.opsForValue()
                .set(EMAIL_VERIFIED_PREFIX + email, "true", Duration.ofMinutes(10));
    }

    // 이메일 인증 완료 여부 확인
    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.toString().equals(
                redisTemplate.opsForValue().get(EMAIL_VERIFIED_PREFIX + email)
        );
    }

    // 이메일 인증 완료 마킹 삭제 (회원가입 완료 후)
    public void deleteEmailVerified(String email) {
        redisTemplate.delete(EMAIL_VERIFIED_PREFIX + email);
    }

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    // 리프레시 토큰 저장
    public void saveRefreshToken(Long userId, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue()
                .set(REFRESH_TOKEN_PREFIX + userId, refreshToken, Duration.ofMillis(ttlMillis));
    }

    // 리프레시 토큰 조회
    public Optional<String> getRefreshToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId));
    }

    // 리프레시 토큰 삭제 (로그아웃)
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    private static final String BLACKLIST_PREFIX = "blacklist:";

    // 액세스 토큰 블랙리스트 등록
    public void addToBlacklist(String accessToken, long ttlMillis) {
        redisTemplate.opsForValue()
                .set(BLACKLIST_PREFIX + accessToken, "logout", Duration.ofMillis(ttlMillis));
    }

    // 블랙리스트 여부 확인
    public boolean isBlacklisted(String accessToken) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + accessToken);
    }

    // 특정 공시 접수번호에 대해 파싱 lock을 획득
    public boolean tryLockDisclosureParsing(String rceptNo, long ttlMinutes) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(
                        DISCLOSURE_PARSE_LOCK_PREFIX + rceptNo,
                        "locked",
                        Duration.ofMinutes(ttlMinutes)
                );

        return Boolean.TRUE.equals(result);
    }

    // 특정 공시 접수번호의 파싱 lock을 해제
    // 파싱 성공 또는 실패 후 lock을 제거해 다음 실행에서 다시 처리 가능하게 함
    public void unlockDisclosureParsing(String rceptNo) {
        redisTemplate.delete(DISCLOSURE_PARSE_LOCK_PREFIX + rceptNo);
    }

    // 특정 공시 접수번호의 파싱 실패 기록을 저장
    // 실패한 공시를 너무 자주 재시도하면 DART 호출과 서버 자원을 낭비하므로 짧게 막아둠
    public void markDisclosureParsingFailed(String rceptNo, long ttlHours) {
        redisTemplate.opsForValue()
                .set(
                        DISCLOSURE_PARSE_FAILED_PREFIX + rceptNo,
                        "failed",
                        Duration.ofHours(ttlHours)
                );
    }

    // 특정 공시 접수번호가 최근 파싱 실패 상태인지 확인
    // 실패 상태라면 이번 스케줄러에서는 건너뜀
    public boolean isDisclosureParsingRecentlyFailed(String rceptNo) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(DISCLOSURE_PARSE_FAILED_PREFIX + rceptNo)
        );
    }

    // 특정 공시 접수번호의 파싱 실패 캐시를 즉시 삭제
    public void clearDisclosureParsingFailedKey(String rceptNo) {
        redisTemplate.delete(DISCLOSURE_PARSE_FAILED_PREFIX + rceptNo);
    }

    // 모든 공시 파싱 실패 캐시를 일괄 삭제
    // DART 연결 오류 수정 후 즉시 재시도할 때 사용 (6시간 TTL 만료 전 수동 초기화)
    public int clearAllDisclosureParsingFailedKeys() {
        Set<String> keys = redisTemplate.keys(DISCLOSURE_PARSE_FAILED_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        redisTemplate.delete(keys);
        return keys.size();
    }

    // 특정 rceptNo 목록의 파싱 실패 캐시를 삭제
    public int clearDisclosureParsingFailedKeys(List<String> rceptNos) {
        List<String> keys = rceptNos.stream()
                .map(rceptNo -> DISCLOSURE_PARSE_FAILED_PREFIX + rceptNo)
                .toList();
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted.intValue() : 0;
    }
}