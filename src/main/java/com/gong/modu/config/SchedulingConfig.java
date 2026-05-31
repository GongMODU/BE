package com.gong.modu.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Spring 의 @Scheduled 기능을 활성화하는 설정 클래스
// scheduler.enabled=false 면 빈 자체가 등록되지 않아 @EnableScheduling 이 적용 안 되고
// 모든 @Scheduled 메서드(ExternalDataScheduler 의 새벽 배치 전체)가 일절 실행되지 않는다.
// 발표 등 토큰 비용 일시 차단이 필요할 때 application-prod.properties 한 줄로 전체 OFF 가능.
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
