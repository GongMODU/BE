package com.gong.modu.service.user;

import com.gong.modu.domain.dto.user.InterestIpoItemResponse;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.entity.ipo.IpoEventBroker;
import com.gong.modu.domain.entity.ipo.IpoOffering;
import com.gong.modu.domain.entity.user.User;
import com.gong.modu.domain.entity.user.UserInterestIpo;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.IpoEventBrokerRepository;
import com.gong.modu.repository.ipo.IpoEventRepository;
import com.gong.modu.repository.user.UserInterestIpoRepository;
import com.gong.modu.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 관심 공모주(찜) 등록/해제/조회를 담당하는 서비스
// 기능명세서 5.1.2 관심 공모주 참고
@Service
@RequiredArgsConstructor
public class InterestIpoService {

    private final UserRepository userRepository;
    private final IpoEventRepository ipoEventRepository;
    private final UserInterestIpoRepository userInterestIpoRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;

    // 특정 공모주를 사용자의 관심 목록에 등록
    @Transactional
    public Long addInterest(Long userId, Long ipoEventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        IpoEvent ipoEvent = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        if (userInterestIpoRepository.existsByUserAndIpoEventId(user, ipoEventId)) {
            throw new CustomException(ErrorCode.ALREADY_INTERESTED_IPO);
        }

        UserInterestIpo saved = userInterestIpoRepository.save(
                UserInterestIpo.builder()
                        .user(user)
                        .ipoEvent(ipoEvent)
                        .build()
        );

        return saved.getId();
    }

    // 특정 공모주를 사용자의 관심 목록에서 해제
    @Transactional
    public void removeInterest(Long userId, Long ipoEventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserInterestIpo target = userInterestIpoRepository.findByUserAndIpoEventId(user, ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERESTED_IPO_NOT_FOUND));

        userInterestIpoRepository.delete(target);
    }

    // 사용자의 관심 공모주 목록을 최신 등록순으로 조회
    @Transactional(readOnly = true)
    public List<InterestIpoItemResponse> getMyInterests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<UserInterestIpo> interests = userInterestIpoRepository.findByUserOrderByCreatedAtDesc(user);
        LocalDate today = LocalDate.now();

        return interests.stream()
                .map(interest -> toItemResponse(interest, today))
                .toList();
    }

    // 단건 상세 화면에서 찜 여부를 확인하는 용도 (existsByUserAndIpoEventId 단일 쿼리)
    @Transactional(readOnly = true)
    public boolean isInterested(Long userId, Long ipoEventId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> userInterestIpoRepository.existsByUserAndIpoEventId(user, ipoEventId))
                .orElse(false);
    }

    // 로그인 사용자의 관심 등록된 IpoEvent ID Set을 한 번에 조회
    // 홈 화면에서 isFavorited 표시 시 N+1 쿼리를 막기 위한 용도
    @Transactional(readOnly = true)
    public Set<Long> getInterestedIpoEventIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }

        return userRepository.findById(userId)
                .map(user -> userInterestIpoRepository.findByUser(user).stream()
                        .map(interest -> interest.getIpoEvent().getId())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private InterestIpoItemResponse toItemResponse(UserInterestIpo interest, LocalDate today) {
        IpoEvent event = interest.getIpoEvent();
        IpoOffering offering = event.getOffering();

        List<String> brokerNames = ipoEventBrokerRepository.findByIpoEventId(event.getId()).stream()
                .map(IpoEventBroker::getBrokerName)
                .distinct()
                .toList();

        return InterestIpoItemResponse.builder()
                .interestId(interest.getId())
                .ipoEventId(event.getId())
                .companyName(event.getCompany().getCorpName())
                .status(event.resolveStatus(today))
                .subscriptionStartDate(event.getSubscriptionStartDate())
                .subscriptionEndDate(event.getSubscriptionEndDate())
                .listingDate(event.getListingDate())
                .listingDateEstimated(event.getListingDateEstimated())
                .lockupExpiryDate(event.getLockupExpiryDate())
                .lockupExpiryDateEstimated(event.getLockupExpiryDateEstimated())
                .offerPriceMin(offering == null ? null : offering.getOfferPriceMin())
                .offerPriceMax(offering == null ? null : offering.getOfferPriceMax())
                .offerPrice(offering == null ? null : offering.getOfferPrice())
                .brokerNames(brokerNames)
                .interestedAt(interest.getCreatedAt())
                .build();
    }
}
