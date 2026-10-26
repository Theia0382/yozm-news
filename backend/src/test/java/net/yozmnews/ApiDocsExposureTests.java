package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** 명세 엔드포인트는 기본 설정에서 닫혀 있다 — 테스트와 local 프로필만 켠다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiDocsExposureTests {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@LocalServerPort
	int port;

	@Test
	void apiDocsAreNotExposedByDefault() throws Exception {
		for (String path : new String[] { "/v3/api-docs", "/v3/api-docs.yaml" }) {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).build();
			assertThat(this.httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).as(path)
				.isEqualTo(404);
		}
	}

}
