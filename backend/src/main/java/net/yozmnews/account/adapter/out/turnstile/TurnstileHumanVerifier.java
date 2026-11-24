package net.yozmnews.account.adapter.out.turnstile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import net.yozmnews.account.domain.humancheck.HumanCheckToken;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.account.domain.humancheck.TurnstileAnswers;
import net.yozmnews.platform.CappedBody;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.SecretValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare Turnstile 검증기 — 토큰 · 비밀 키 · 클라이언트 주소를 siteverify 에 보내고 답을 {@link TurnstileAnswers} 로 판정한다.
 * docs/specs/user.md §4 · user-R41
 * <p>
 * 주소는 코드 상수다 — 운영 설정으로 바꿀 수 없다. 리다이렉트를 따르지 않고 프록시를 쓰지 않는다. 연결 · 응답 마감과 본문 상한을 둔다. 로그 ·
 * 문구에 토큰 · 비밀 키를 싣지 않는다. 비밀 키가 틀렸다는 답은 거부이고 ERROR 로그를 남긴다 — 운영 설정 오류를 숨기지 않는다.
 */
final class TurnstileHumanVerifier implements HumanVerifier, AutoCloseable {

	/** Turnstile 서버 검증 주소 */
	static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	/** 연결 마감 */
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	/** 응답 마감 — 본문 읽기까지 덮는다 */
	static final Duration RESPONSE_DEADLINE = Duration.ofSeconds(5);

	/** 응답 본문 상한(바이트) */
	static final int MAX_RESPONSE_BYTES = 65_536;

	private static final Logger logger = LoggerFactory.getLogger(TurnstileHumanVerifier.class);

	private final URI endpoint;

	private final SecretValue secret;

	private final Duration deadline;

	private final HttpClient client;

	TurnstileHumanVerifier(SecretValue secret) {
		this(SITEVERIFY_URL, secret, RESPONSE_DEADLINE);
	}

	/**
	 * @param endpoint 프로덕션은 {@link #SITEVERIFY_URL} 이다 — 시험이 대체 서버의 주소를 넣는다
	 */
	TurnstileHumanVerifier(String endpoint, SecretValue secret, Duration deadline) {
		this.endpoint = URI.create(Objects.requireNonNull(endpoint, "endpoint"));
		this.secret = Objects.requireNonNull(secret, "secret");
		this.deadline = Objects.requireNonNull(deadline, "deadline");
		this.client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY)
			.build();
	}

	@Override
	public HumanCheckResult verify(HumanCheckToken token, GuardedAction action, ClientAddress client) {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(client, "client");
		String form = "secret=" + encode(this.secret.reveal()) + "&response=" + encode(token.value()) + "&remoteip="
				+ encode(client.literal());
		HttpRequest request = HttpRequest.newBuilder(this.endpoint)
			.timeout(this.deadline)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
			.build();
		CompletableFuture<HttpResponse<CappedBody.Result>> future = CappedBody.sendAsync(this.client, request,
				MAX_RESPONSE_BYTES);
		try {
			HttpResponse<CappedBody.Result> response = future.get(this.deadline.toNanos(), TimeUnit.NANOSECONDS);
			HumanCheckResult result = switch (response.body()) {
				case CappedBody.Result.Overflow overflow -> new HumanCheckResult.Unavailable(
						"Turnstile 검증 응답 본문이 상한(" + MAX_RESPONSE_BYTES + " 바이트)을 넘는다");
				case CappedBody.Result.Complete complete ->
					TurnstileAnswers.classify(response.statusCode(), complete.text(), action);
			};
			if (TurnstileAnswers.secretRejected(result)) {
				logger.error("Turnstile 이 비밀 키를 받지 않았다 — 설정 {}(환경 변수 {})를 확인한다",
						HumanCheckSetting.SECRET_KEY_PROPERTY, HumanCheckSetting.SECRET_KEY_ENVIRONMENT_VARIABLE);
			}
			return result;
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			return deadlinePassed();
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			return new HumanCheckResult.Unavailable("Turnstile 검증을 기다리다 중단됐다");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof HttpConnectTimeoutException) {
				return new HumanCheckResult.Unavailable(
						"Turnstile 연결 마감(" + CONNECT_TIMEOUT.toSeconds() + "초)을 넘었다");
			}
			if (cause instanceof HttpTimeoutException) {
				return deadlinePassed();
			}
			if (cause instanceof IOException) {
				return new HumanCheckResult.Unavailable(
						"Turnstile 과 주고받지 못했다 — " + cause.getClass().getSimpleName());
			}
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Turnstile 검증 중 예상 못한 오류", cause);
		}
	}

	@Override
	public void close() {
		this.client.close();
	}

	@Override
	public String toString() {
		return "TurnstileHumanVerifier[" + this.endpoint + "]";
	}

	private HumanCheckResult deadlinePassed() {
		return new HumanCheckResult.Unavailable("Turnstile 응답 마감(" + this.deadline.toMillis() + "ms)을 넘었다");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
