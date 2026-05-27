package com.gong.modu.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

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
        // TLS 관련 설정(TLSv1.2 강제, RSA cipher 허용 등)은 JVM 옵션과 ModuApplication.main()
        // 의 Security.setProperty 로 처리하므로 여기선 명시하지 않음 (Netty 기본 SSL 사용).
        // 명시적 SslContext 부여 시 Netty/JDK SSL stack 과 충돌해 핸드셰이크 행 발생 가능.
        //
        // 무한 대기 방지용 timeout 만 명시.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(15))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000);

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
