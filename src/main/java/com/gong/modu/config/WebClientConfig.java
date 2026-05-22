package com.gong.modu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// 외부 HTTP API 호출에 사용할 WebClient Bean들을 등록하는 설정 클래스
@Configuration
public class WebClientConfig {

    // DART 공시 원문 ZIP은 투자설명서 등 큰 문서가 수 MB에 달하므로 메모리 버퍼 한도를 넉넉히 설정
    private static final int DART_MAX_IN_MEMORY_SIZE = 64 * 1024 * 1024;

    @Value("${external.dart.base-url}")
    private String dartBaseUrl;

    @Value("${external.kis.base-url}")
    private String kisBaseUrl;

    @Bean
    public WebClient dartWebClient() {
        return WebClient.builder()
                .baseUrl(dartBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(DART_MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Bean
    public WebClient kisWebClient() {
        return WebClient.builder()
                .baseUrl(kisBaseUrl)
                .build();
    }

    @Bean
    public WebClient youtubeWebClient() {
        return WebClient.builder()
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }
}
