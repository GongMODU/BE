package com.gong.modu.service.pipeline;

import com.gong.modu.domain.entity.ipo.Broker;
import com.gong.modu.domain.entity.ipo.IpoEvent;
import com.gong.modu.domain.entity.ipo.IpoEventBroker;
import com.gong.modu.domain.enums.ipo.BrokerRole;
import com.gong.modu.repository.ipo.BrokerRepository;
import com.gong.modu.repository.ipo.IpoEventBrokerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 증권사명 목록을 Broker 마스터와 IpoEventBroker 연결 정보로 저장하는 컴포넌트
// 공시 파싱 결과의 주관사 목록을 공모 이벤트에 반영할 때 사용
@Component
@RequiredArgsConstructor
public class IpoBrokerWriter {

    private final BrokerRepository brokerRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;

    // 증권사명 목록을 받아 해당 공모 이벤트의 IpoEventBroker로 저장하는 메서드 (이미 연결된 증권사는 건너뜀)
    public void upsertBrokers(IpoEvent ipoEvent, List<String> brokerNames) {
        if (brokerNames == null || brokerNames.isEmpty()) {
            return;
        }

        // 이미 이 공모 이벤트에 연결된 증권사명 목록
        List<IpoEventBroker> existing = ipoEventBrokerRepository.findByIpoEventId(ipoEvent.getId());

        for (String rawName : brokerNames) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }

            String brokerName = rawName.trim();

            // 이미 연결된 증권사면 건너뜀
            boolean alreadyLinked = existing.stream()
                    .anyMatch(eventBroker -> brokerName.equals(eventBroker.getBrokerName()));

            if (alreadyLinked) {
                continue;
            }

            // 증권사 마스터는 이름 기준으로 찾거나 새로 생성
            Broker broker = brokerRepository.findByName(brokerName)
                    .orElseGet(() -> brokerRepository.save(
                            Broker.builder()
                                    .name(brokerName)
                                    .build()
                    ));

            ipoEventBrokerRepository.save(
                    IpoEventBroker.builder()
                            .ipoEvent(ipoEvent)
                            .broker(broker)
                            .brokerName(brokerName)
                            .role(BrokerRole.UNDERWRITER) // 기본 역할은 인수회사로 설정
                            .build()
            );
        }
    }
}
