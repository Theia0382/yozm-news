package net.yozmnews.llm.adapter.out.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * 공급자로 가는 전송의 비동기 꼴 — 동기 꼴과 같은 마감 · 본문 상한 · 닫힘이 실패한 미래로 끝난다. 동기 꼴은 공급자 클라이언트 시험이 본다.
 * <p>
 * 지키는 규칙: llm-R7 · llm-R13 · llm-R14
 */
class JdkTransportTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String BODY = "0123456789";

	@Test
	void anAsyncRequestCarriesItsHeadersAndReadsTheBody() {
		server.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody(BODY)));

		try (JdkTransport transport = new JdkTransport(Duration.ofSeconds(5), 10)) {
			JdkTransport.Response response = transport.sendAsync(request(Map.of("x-probe", List.of("one")))).join();

			assertThat(response.status()).isEqualTo(200);
			assertThat(response.body()).isEqualTo("0123456789");
		}
		server.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/ping"))
			.withHeader("x-probe", com.github.tomakehurst.wiremock.client.WireMock.equalTo("one")));
	}

	@Test
	void anAsyncBodyOverTheLimitFails() {
		server.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody(BODY)));

		try (JdkTransport transport = new JdkTransport(Duration.ofSeconds(5), 9)) {
			assertThat(transport.sendAsync(request(Map.of()))).failsWithin(Duration.ofSeconds(5))
				.withThrowableOfType(ExecutionException.class)
				.withCauseInstanceOf(TransportFailure.class)
				.withMessageContaining("LLM 공급자의 응답 본문이 상한(9 바이트)을 넘었다");
		}
	}

	@Test
	void aLateAsyncAnswerFailsAtTheDeadline() {
		server.stubFor(get(urlEqualTo("/ping"))
			.willReturn(aResponse().withStatus(200).withBody(BODY).withFixedDelay(2_000)));

		try (JdkTransport transport = new JdkTransport(Duration.ofMillis(300), 10)) {
			assertThat(transport.sendAsync(request(Map.of()))).failsWithin(Duration.ofSeconds(1))
				.withThrowableOfType(ExecutionException.class)
				.withCauseInstanceOf(TransportFailure.class)
				.withMessageContaining("LLM 공급자 응답 마감(300ms)을 넘었다");
		}
	}

	@Test
	void aClosedTransportSendsNoAsyncRequest() {
		server.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody(BODY)));
		JdkTransport transport = new JdkTransport(Duration.ofSeconds(5), 10);
		transport.close();

		assertThat(transport.sendAsync(request(Map.of()))).failsWithin(Duration.ofSeconds(1))
			.withThrowableOfType(ExecutionException.class)
			.withCauseInstanceOf(TransportFailure.class)
			.withMessageContaining("닫은 LLM 공급자 클라이언트에 요청했다");
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	private static JdkTransport.Request request(Map<String, List<String>> headers) {
		return new JdkTransport.Request("GET", URI.create(server.baseUrl() + "/ping"), headers, null);
	}

}
