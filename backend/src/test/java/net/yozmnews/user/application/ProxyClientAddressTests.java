package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.yozmnews.GradleProperties;
import org.junit.jupiter.api.Test;

/**
 * 배포 proxy 의 클라이언트 IP 결선 — Caddyfile 원문이 앱이 읽는 헤더({@link ClientAddresses#HEADER})를 클라이언트 IP 로 덮어쓰고, 신뢰 범위
 * (기본 사설 대역)에서 헤더를 오른쪽부터 읽도록 결선했는지 본다. Caddy 가 그 설정을 어떻게 해석하고 어느 주소를 고르는지는 이 시험 밖이다
 * — 원문만 대조한다. 파일 경로는 Gradle 이 시스템 속성으로 넘긴다({@code backend/build.gradle.kts}).
 * <p>
 * 지키는 규칙: user-R37 · docs/operations/configuration.md "공개"
 */
class ProxyClientAddressTests {

	static final String PATH_PROPERTY = "yozmnews.proxy-caddyfile";

	@Test
	void theProxyOverwritesTheHeaderTheAppReadsWithTheClientIp() throws IOException {
		assertThat(lines()).contains("header_up " + ClientAddresses.HEADER + " {client_ip}");
		// 대조 — 리터럴로도 같은 이름이다(헤더 이름을 바꾸면 Caddyfile 과 함께 바꾼다)
		assertThat(ClientAddresses.HEADER).isEqualTo("X-Real-IP");
	}

	@Test
	void theProxyTrustsPrivateRangesByDefaultAndReadsFromTheRight() throws IOException {
		assertThat(lines()).contains("trusted_proxies static {$YOZMNEWS_TRUSTED_PROXIES:private_ranges}",
				"trusted_proxies_strict", "client_ip_headers {$YOZMNEWS_CLIENT_IP_HEADERS:X-Forwarded-For}");
	}

	/** 앞뒤 공백을 지운 줄 */
	private static List<String> lines() throws IOException {
		Path caddyfile = GradleProperties.pathFrom(PATH_PROPERTY, System.getProperty(PATH_PROPERTY));
		return Files.readAllLines(caddyfile).stream().map(String::strip).toList();
	}

}
