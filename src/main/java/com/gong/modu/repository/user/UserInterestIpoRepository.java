package com.gong.modu.repository.user;

import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.entity.user.UserInterestIpo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 관심 공모주 조회 Repository
public interface UserInterestIpoRepository extends JpaRepository<UserInterestIpo, Long> {

    // 특정 사용자의 관심 공모주 목록 조회
    List<UserInterestIpo> findByUser(User user);

    // 특정 사용자의 관심 공모주 목록을 최신순으로 조회
    List<UserInterestIpo> findByUserOrderByCreatedAtDesc(User user);

    // 특정 사용자가 특정 공모주를 관심 등록했는지 조회
    Optional<UserInterestIpo> findByUserAndIpoEventId(User user, Long ipoEventId);

    // 관심 등록 여부 확인
    boolean existsByUserAndIpoEventId(User user, Long ipoEventId);

    // 관심 등록 해제용 삭제
    void deleteByUserAndIpoEventId(User user, Long ipoEventId);

    void deleteByUser(User user);
}
