package com.gong.modu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    // 각 작업을 담당할 스레드 풀 관리
    @Bean("summaryTaskExecutor")
    public Executor summaryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3); // 기본 스레드 3개 = 영상 3개 동시 처리
        executor.setMaxPoolSize(3); // 최대 3개까지만 허용
        executor.setQueueCapacity(10); // 스레드가 다 바쁠 때 대기열 10개까지 허용
        executor.setThreadNamePrefix("summary-"); // 스레드 이름 구분: summary-N
        executor.initialize();
        return executor;
    }

    // 공시 원문 ZIP 다운로드 + Claude API 호출이 모두 네트워크 I/O이므로 병렬 처리로 속도 개선
    // Claude API rate limit을 고려해 스레드 수를 5개로 제한
    @Bean("disclosureParsingPool")
    public Executor disclosureParsingPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("disclosure-parsing-");
        executor.initialize();
        return executor;
    }
}
