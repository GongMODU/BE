package com.gong.modu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.security.Security;

@EnableJpaAuditing
@SpringBootApplication
public class ModuApplication {

	public static void main(String[] args) {
		// Java 17 기본 jdk.tls.disabledAlgorithms 가 RSA-only cipher 와 일부 keySize 제한을
		// 포함해 DART(opendart.fss.or.kr) 같은 RSA-only TLS 서버와 협상 실패(handshake_failure)
		// 발생. JAVA_TOOL_OPTIONS 로는 값 안에 공백(예: "DH keySize < 1024")이 있어 전달 불가하므로
		// 앱 시작 시점에 Security.setProperty 로 직접 설정한다.
		// (TLSv1/v1.1/SSLv3, RC4, DES, MD5withRSA, 3DES, NULL, anon 같은 진짜 위험 항목만 남기고
		//  keySize 제한, jdk.disabled.namedCurves 등은 제거 → RSA cipher 협상 가능)
		Security.setProperty(
				"jdk.tls.disabledAlgorithms",
				"SSLv3, TLSv1, TLSv1.1, RC4, DES, MD5withRSA, 3DES_EDE_CBC, anon, NULL"
		);

		SpringApplication.run(ModuApplication.class, args);
	}

}
