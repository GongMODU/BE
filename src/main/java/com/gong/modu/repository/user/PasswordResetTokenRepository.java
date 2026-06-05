package com.gong.modu.repository.user;

import com.gong.modu.domain.entity.user.PasswordResetToken;
import com.gong.modu.domain.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 토큰 문자열로 재설정 토큰 조회
    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);
}