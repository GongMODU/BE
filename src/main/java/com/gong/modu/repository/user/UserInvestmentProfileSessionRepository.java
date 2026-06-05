package com.gong.modu.repository.user;

import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.entity.user.UserInvestmentProfileSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInvestmentProfileSessionRepository extends JpaRepository<UserInvestmentProfileSession, Long> {

    Optional<UserInvestmentProfileSession> findByUser(User user);

    void deleteByUser(User user);
}
