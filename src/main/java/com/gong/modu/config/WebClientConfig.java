package com.gong.modu.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

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
    public WebClient dartWebClient() throws SSLException {
        // DART(opendart.fss.or.kr) 는 TLSv1.2 + RSA-only cipher(AES128-GCM-SHA256) 만 받음.
        // Java 17 기본 cipher set 이 신규 cipher 우선이라 협상 실패하므로 TLSv1.2 만 강제하고
        // cipher 선택은 JVM 옵션의 disabledAlgorithms 완화로 RSA cipher 가 협상에 포함되도록 함.
        // (JAVA_TOOL_OPTIONS 에서 disabledAlgorithms 조정함 - docker-compose.yml 참고)
        SslContext sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.2")
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> sslSpec.sslContext(sslContext));

        return WebClient.builder()
                .baseUrl(dartBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
