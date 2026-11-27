package net.yozmnews.account.adapter.out.turnstile;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.StallingRelay;
import net.yozmnews.TrustingTls;
import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.account.domain.humancheck.HumanCheckToken;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Turnstile 검증기 — WireMock 을 siteverify 자리에 두고 요청 형태(폼 · 비밀 키 · 토큰 · 클라이언트 주소), 답의 판정, 닿지 못하는 갈래(5xx ·
 * 마감 · 본문 상한 · 연결 실패 · 리다이렉트), 로그에 토큰 · 비밀 키가 남지 않는지를 본다. 응답 본문은 Turnstile 서버 검증 문서의 모양이다.
 * 신호 없이 끊긴 연결 뒤 새 연결로 이어 가는지는 HTTPS 전용 서버와 정지 중계({@code StallingRelay})로 본다.
 * <p>
 * 지키는 규칙: account-R33 · platform-R135 · docs/overview.md §6 비밀 값(비밀 값이 로그에 남지 않는다)
 */
@ExtendWith(OutputCaptureExtension.class)
class TurnstileHumanVerifierTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	/** 끊긴 연결에서의 회복 시험이 쓰는 HTTPS 전용 서버 — ALPN 으로 HTTP/2 를 협상할 수 있다 */
	@RegisterExtension
	static WireMockExtension tls = WireMockExtension.newInstance()
		.options(wireMockConfig().httpDisabled(true).dynamicHttpsPort().gzipDisabled(true))
		.build();

	private static final String PATH = "/turnstile/v0/siteverify";

	private static final String SECRET = "0x4AAAAAAAwiremock-only-secret";

	private static final String TOKEN = "0.wiremock-only-token";

	private static final HumanCheckToken HUMAN = HumanCheckToken.parse(TOKEN).orElseThrow();

	private static final ClientAddress CLIENT = ClientAddress.parse("203.0.113.7").orElseThrow();

	private final List<TurnstileHumanVerifier> verifiers = new ArrayList<>();

	@AfterEach
	void close() {
		this.verifiers.forEach(TurnstileHumanVerifier::close);
	}

	@Test
	void sendsTheSecretTokenAndClientAsAFormAndPassesTheSameAction() {
		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":true,"challenge_ts":"2026-11-14T10:00:00.000Z","hostname":"news.example.com",
				"error-codes":[],"action":"account-recovery","cdata":""}""")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.ACCOUNT_RECOVERY, CLIENT))
			.isEqualTo(new HumanCheckResult.Passed());
		server.verify(1, postRequestedFor(urlEqualTo(PATH))
			.withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
			.withRequestBody(equalTo("secret=0x4AAAAAAAwiremock-only-secret&response=0.wiremock-only-token"
					+ "&remoteip=203.0.113.7")));
	}

	@Test
	void anotherActionIsRejected() {
		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[],"action":"sign-up"}""")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("action-mismatch"));
	}

	@Test
	void spentTokensAndInternalErrorsAreRejected() {
		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":false,"error-codes":["timeout-or-duplicate"]}""")));
		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("timeout-or-duplicate"));

		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":false,"error-codes":["internal-error"]}""")));
		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("internal-error"));
	}

	@Test
	void aWrongSecretIsRejectedAndLoggedWithoutTheSecretOrToken(CapturedOutput output) {
		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":false,"error-codes":["invalid-input-secret"]}""")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isEqualTo(new HumanCheckResult.Rejected("invalid-input-secret"));
		assertThat(output.getOut()).contains("ERROR")
			.contains("YOZMNEWS_HUMAN_CHECK_TURNSTILE_SECRET_KEY")
			.doesNotContain(SECRET)
			.doesNotContain(TOKEN);
	}

	@Test
	void serverErrorsAreUnavailable() {
		server.stubFor(post(PATH).willReturn(json(503, """
				{"success":false,"error-codes":["internal-error"]}""")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
	}

	@Test
	void aSlowAnswerPastTheDeadlineIsUnavailable() {
		server.stubFor(post(PATH).willReturn(json(200, """
				{"success":true,"action":"sign-in"}""").withFixedDelay(1500)));

		assertThat(verifier(Duration.ofMillis(300)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
	}

	@Test
	void aBodyOverTheCapIsUnavailable() {
		server.stubFor(post(PATH).willReturn(json(200, "{\"success\":true,\"action\":\"sign-in\",\"pad\":\""
				+ "x".repeat(65_536) + "\"}")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
	}

	@Test
	void aRedirectIsNotFollowed() {
		server.stubFor(post(PATH).willReturn(aResponse().withStatus(302).withHeader("Location", "/elsewhere")));
		server.stubFor(post("/elsewhere").willReturn(json(200, """
				{"success":true,"action":"sign-in"}""")));

		assertThat(verifier(Duration.ofSeconds(2)).verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
		server.verify(0, postRequestedFor(urlEqualTo("/elsewhere")));
	}

	@Test
	void aRefusedConnectionIsUnavailable() throws IOException {
		int closed;
		try (ServerSocket socket = new ServerSocket(0)) {
			closed = socket.getLocalPort();
		}
		TurnstileHumanVerifier unreachable = new TurnstileHumanVerifier("http://127.0.0.1:" + closed + PATH,
				SecretValue.of(SECRET), Duration.ofSeconds(2));
		this.verifiers.add(unreachable);

		assertThat(unreachable.verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
			.isInstanceOf(HumanCheckResult.Unavailable.class);
	}

	@Test
	void theDefaultEndpointIsTheTurnstileSiteverifyAddress() {
		assertThat(TurnstileHumanVerifier.SITEVERIFY_URL)
			.isEqualTo("https://challenges.cloudflare.com/turnstile/v0/siteverify");
	}

	@Test
	void recoversOnANewConnectionAfterTheDeadline() throws IOException {
		tls.stubFor(post(PATH).willReturn(json(200, """
				{"success":true,"hostname":"news.example.com","error-codes":[],"action":"sign-in"}""")));

		try (StallingRelay relay = new StallingRelay(tls.getHttpsPort())) {
			TurnstileHumanVerifier verifier = TrustingTls.whileDefault(() -> new TurnstileHumanVerifier(
					"https://localhost:" + relay.port() + PATH, SecretValue.of(SECRET), Duration.ofSeconds(2)));
			this.verifiers.add(verifier);

			assertThat(verifier.verify(HUMAN, GuardedAction.SIGN_IN, CLIENT)).isEqualTo(new HumanCheckResult.Passed());
			relay.stallExisting();
			assertThat(verifier.verify(HUMAN, GuardedAction.SIGN_IN, CLIENT))
				.isEqualTo(new HumanCheckResult.Unavailable("Turnstile 응답 마감(2000ms)을 넘었다"));
			// 닿지 못한 검증은 부르는 쪽이 통과시키므로(ADR-0172), 여기서 다시 닿아야 봇 차단이 꺼진 채로 남지 않는다
			assertThat(verifier.verify(HUMAN, GuardedAction.SIGN_IN, CLIENT)).isEqualTo(new HumanCheckResult.Passed());
			assertThat(relay.acceptedConnections()).isEqualTo(2);
		}
	}

	private TurnstileHumanVerifier verifier(Duration deadline) {
		TurnstileHumanVerifier verifier = new TurnstileHumanVerifier(server.baseUrl() + PATH, SecretValue.of(SECRET),
				deadline);
		this.verifiers.add(verifier);
		return verifier;
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

}
