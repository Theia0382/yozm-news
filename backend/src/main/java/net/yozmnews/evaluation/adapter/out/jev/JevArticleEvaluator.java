package net.yozmnews.evaluation.adapter.out.jev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;

import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure.Kind;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.jev.JevApiKey;
import net.yozmnews.evaluation.domain.jev.JevErrors;
import net.yozmnews.evaluation.domain.jev.JevOutcome;
import net.yozmnews.evaluation.domain.jev.JevRequest;
import net.yozmnews.evaluation.domain.jev.JevResponse;

/**
 * Jev(TypeSafe System One) 평가기 — docs/specs/evaluation.md §4 구현과 로컬 대체물.
 * <p>
 * 기사 하나에 요청 하나를 보낸다. 문맥 초과면 같은 질문을 빈 state 로 한 번 더 물어 기사 탓인지 체계 탓인지 가른다 — 확인 요청의 답은
 * 평가로 쓰지 않는다. 요청 하나(헤더 · 본문 전부)에 마감을 걸고, 응답 본문은 상한까지만 받는다. 클라이언트는 리다이렉트를 따르지 않고 시스템
 * 프록시를 읽지 않으며, 요청을 다시 보내지 않는다 — 재시도는 평가 대기가 한다.
 * <p>
 * 키는 {@code Authorization} 헤더로만 나간다. 실패 문구에는 요청 헤더 · 요청 본문 · 응답 본문 원문을 담지 않는다.
 */
public final class JevArticleEvaluator implements ArticleEvaluator, AutoCloseable {

	/** 연결 마감 */
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/**
	 * 요청 하나(헤더 · 본문 전부)의 마감 — 회차 한 묶음({@code PendingEvaluations.CLAIM_BATCH}) × 기사당 요청 둘 × 이 마감이 점유 기간
	 * ({@link net.yozmnews.evaluation.domain.queue.EvaluationSchedule#LEASE}) 안에 들어야 한다
	 */
	static final Duration RESPONSE_DEADLINE = Duration.ofSeconds(15);

	/** 응답 본문의 상한(바이트) */
	static final int MAX_RESPONSE_BYTES = 1_048_576;

	/** System One 경로 — 기본 주소 뒤에 붙인다 */
	static final String PATH = "/v1/systemone";

	/** 요청 ID 응답 헤더 */
	static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

	static final String REQUEST_FAILED = "Jev 가 평가 요청을 받지 않았다";

	static final String ARTICLE_TOO_LONG = "기사가 Jev 문맥 한도를 넘는다 — 빈 state 확인 요청은 받았다";

	static final String PROBE_FAILED = "Jev 문맥 초과 — 빈 state 확인 요청도 받지 않았다";

	static final String BODY_TOO_LARGE = "Jev 응답 본문이 상한(" + MAX_RESPONSE_BYTES + " 바이트)을 넘는다";

	private final HttpClient client;

	private final URI endpoint;

	private final String model;

	private final JevApiKey key;

	private final Duration deadline;

	/**
	 * @param baseUrl 기본 주소 — 끝의 {@code /} 는 떼고 {@value #PATH} 를 붙인다
	 */
	JevArticleEvaluator(URI baseUrl, String model, JevApiKey key) {
		this(baseUrl, model, key, RESPONSE_DEADLINE);
	}

	/** 시험이 마감을 줄인다 */
	JevArticleEvaluator(URI baseUrl, String model, JevApiKey key, Duration deadline) {
		String base = Objects.requireNonNull(baseUrl, "baseUrl").toString();
		this.endpoint = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + PATH);
		this.model = Objects.requireNonNull(model, "model");
		this.key = Objects.requireNonNull(key, "key");
		this.deadline = Objects.requireNonNull(deadline, "deadline");
		this.client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY)
			.build();
	}

	@Override
	public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(tagSet, "tagSet");
		return switch (send(JevRequest.body(this.model, input, tagSet))) {
			case Exchange.Broken broken -> new EvaluationFailure(Kind.TRANSIENT, broken.message());
			case Exchange.TooLarge tooLarge -> new EvaluationFailure(Kind.EVALUATOR_UNAVAILABLE, BODY_TOO_LARGE);
			case Exchange.Answered answered when answered.status() == 200 ->
				JevResponse.read(answered.body(), answered.requestId(), tagSet);
			case Exchange.Answered answered -> switch (JevErrors.classify(answered.status(), answered.body())) {
				case JevOutcome.Failed failed -> new EvaluationFailure(failed.kind(), message(REQUEST_FAILED, answered));
				case JevOutcome.ContextExceeded exceeded -> probe(answered, tagSet);
			};
		};
	}

	/** 문맥 초과의 원인을 같은 질문 · 빈 state 로 가른다 */
	private EvaluationFailure probe(Exchange.Answered original, TagSetView tagSet) {
		return switch (send(JevRequest.probeBody(this.model, tagSet))) {
			case Exchange.Broken broken -> new EvaluationFailure(Kind.TRANSIENT,
					message("Jev 문맥 초과 — 빈 state 확인 요청이 실패했다: " + broken.message(), original));
			case Exchange.TooLarge tooLarge -> new EvaluationFailure(Kind.EVALUATOR_UNAVAILABLE,
					"Jev 문맥 초과 — 빈 state 확인 요청: " + BODY_TOO_LARGE);
			case Exchange.Answered probe -> {
				Kind kind = JevErrors.afterProbe(probe.status(), probe.body());
				yield new EvaluationFailure(kind, switch (kind) {
					case ARTICLE_REJECTED -> message(ARTICLE_TOO_LONG, original);
					case TRANSIENT, EVALUATOR_UNAVAILABLE -> message(PROBE_FAILED, probe);
				});
			}
		};
	}

	private static String message(String what, Exchange.Answered answered) {
		return JevErrors.message(what, answered.status(), answered.body(), answered.retryAfter(), answered.requestId());
	}

	private Exchange send(String body) {
		HttpRequest request = HttpRequest.newBuilder(this.endpoint)
			.timeout(this.deadline)
			.header("Authorization", this.key.authorization())
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();
		CompletableFuture<HttpResponse<CappedBody.Result>> future = this.client.sendAsync(request,
				info -> new CappedBody(MAX_RESPONSE_BYTES));
		try {
			HttpResponse<CappedBody.Result> response = future.get(this.deadline.toNanos(), TimeUnit.NANOSECONDS);
			return switch (response.body()) {
				case CappedBody.Result.Overflow overflow -> new Exchange.TooLarge();
				case CappedBody.Result.Complete complete -> new Exchange.Answered(response.statusCode(), complete.text(),
						header(response, REQUEST_ID_HEADER), header(response, "retry-after"));
			};
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			return deadlinePassed();
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			return new Exchange.Broken("Jev 요청을 기다리다 중단됐다");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof HttpConnectTimeoutException) {
				return new Exchange.Broken("Jev 연결 마감(" + CONNECT_TIMEOUT.toSeconds() + "초)을 넘었다");
			}
			if (cause instanceof HttpTimeoutException) {
				return deadlinePassed();
			}
			if (cause instanceof IOException) {
				return new Exchange.Broken("Jev 와 주고받지 못했다 — " + cause.getClass().getSimpleName());
			}
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Jev 요청 중 예상 못한 오류", cause);
		}
	}

	private Exchange.Broken deadlinePassed() {
		return new Exchange.Broken("Jev 응답 마감(" + this.deadline.toSeconds() + "초)을 넘었다");
	}

	private static @Nullable String header(HttpResponse<?> response, String name) {
		return response.headers().firstValue(name).orElse(null);
	}

	@Override
	public void close() {
		this.client.close();
	}

	/** 요청 하나의 결말 */
	private sealed interface Exchange {

		/** 응답이 왔다 */
		record Answered(int status, String body, @Nullable String requestId, @Nullable String retryAfter)
				implements Exchange {

		}

		/** 응답 본문이 상한을 넘었다 */
		record TooLarge() implements Exchange {

		}

		/** 연결 · 마감 · 입출력으로 응답을 받지 못했다 — 문구는 고정 문구와 예외 클래스 이름만 */
		record Broken(String message) implements Exchange {

		}

	}

	/**
	 * 상한까지 모으는 본문 수신자 — 상한을 넘으면 받기를 멈추고 넘김을 결과로 남긴다(입출력 오류와 섞지 않는다). 본문을 끝까지 받아야 완료되므로
	 * 요청의 마감이 본문 읽기까지 덮는다.
	 */
	private static final class CappedBody implements HttpResponse.BodySubscriber<CappedBody.Result> {

		private final int max;

		private final ByteArrayOutputStream received = new ByteArrayOutputStream();

		private final CompletableFuture<Result> result = new CompletableFuture<>();

		private Flow.@Nullable Subscription subscription;

		CappedBody(int max) {
			this.max = max;
		}

		@Override
		public CompletionStage<Result> getBody() {
			return this.result;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(List<ByteBuffer> items) {
			if (this.result.isDone()) {
				return;
			}
			for (ByteBuffer item : items) {
				if (this.received.size() + item.remaining() > this.max) {
					this.result.complete(new Result.Overflow());
					Objects.requireNonNull(this.subscription, "subscription").cancel();
					return;
				}
				byte[] bytes = new byte[item.remaining()];
				item.get(bytes);
				this.received.writeBytes(bytes);
			}
		}

		@Override
		public void onError(Throwable throwable) {
			this.result.completeExceptionally(throwable);
		}

		@Override
		public void onComplete() {
			this.result.complete(new Result.Complete(this.received.toString(StandardCharsets.UTF_8)));
		}

		sealed interface Result {

			record Complete(String text) implements Result {

			}

			record Overflow() implements Result {

			}

		}

	}

}
