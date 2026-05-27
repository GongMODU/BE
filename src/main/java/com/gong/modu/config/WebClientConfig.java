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
import java.util.List;

// 외부 HTTP API 호출에 사용할 WebClient Bean들을 등록하는 설정 클래스
@Configuration
public class WebClientConfig {

    // DART 공시 원문 ZIP은 투자설명서 등 큰 문서가 수 MB에 달하므로 메모리 버퍼 한도를 넉넉히 설정
    private static final int DART_MAX_IN_MEMORY_SIZE = 64 * 1024 * 1024;

    // DART 서버가 받는 보편적인 TLSv1.2 cipher 목록 (OpenSSL/curl 기본 cipher 와 동일)
    // Java 17 기본 cipher set 일부를 DART 가 거부해 handshake_failure 가 발생하므로 명시 강제.
    private static final List<String> DART_TLS_CIPHERS = List.of(
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384"
    );

    @Value("${external.dart.base-url}")
    private String dartBaseUrl;

    @Value("${external.kis.base-url}")
    private String kisBaseUrl;

    @Bean
    public WebClient dartWebClient() throws SSLException {
        // DART(opendart.fss.or.kr) 가 Java 17 기본 cipher/protocol 일부를 거부해
        // SSLHandshakeException(handshake_failure) 가 발생하는 문제 해결:
        // - TLSv1.2 만 사용
        // - OpenSSL/curl 과 동일한 보편 cipher list 강제
        SslContext sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.2")
                .ciphers(DART_TLS_CIPHERS)
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
