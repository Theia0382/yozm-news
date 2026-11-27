package net.yozmnews.platform;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.StallingRelay;
import net.yozmnews.TrustingTls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * 외부 API 클라이언트의 공용 구성 — 구성 값과, 회복 시험이 쓰는 시험 장치(중계 + WireMock HTTPS)가 HTTP/2 를 협상하는지 본다.
 * <p>
 * 끊긴 연결에서의 회복은 구성을 쓰는 어댑터의 시험이 어댑터를 직접 세워 본다(JevArticleEvaluatorTests · OpenAiCompatibleEmbedderTests ·
 * TurnstileHumanVerifierTests). 그 시험의 통과가 "시험 장치가 HTTP/2 를 못 해서"가 아님을 여기서 지킨다. 지키는 규칙: platform-R135
 */
class ExternalHttpClientsTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().dynamicHttpsPort().gzipDisabled(true))
		.build();

	@Test
	void buildsHttp11WithoutRedirectsOrProxy() {
		try (HttpClient client = ExternalHttpClients.builder(Duration.ofSeconds(5)).build()) {
			assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
			assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
			assertThat(client.proxy()).isEqualTo(Optional.of(HttpClient.Builder.NO_PROXY));
			assertThat(client.connectTimeout()).isEqualTo(Optional.of(Duration.ofSeconds(5)));
		}
	}

	@Test
	void theRelayNegotiatesHttp2WhenAsked() throws Exception {
		server.stubFor(post("/probe").willReturn(aResponse().withStatus(200).withBody("ok")));

		try (StallingRelay relay = new StallingRelay(server.getHttpsPort());
				HttpClient client = ExternalHttpClients.builder(Duration.ofSeconds(5))
					.version(HttpClient.Version.HTTP_2)
					.sslContext(TrustingTls.context())
					.build()) {
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder(URI.create("https://localhost:" + relay.port() + "/probe"))
						.timeout(Duration.ofSeconds(5))
						.POST(HttpRequest.BodyPublishers.ofString("{}"))
						.build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
		}
	}

}
