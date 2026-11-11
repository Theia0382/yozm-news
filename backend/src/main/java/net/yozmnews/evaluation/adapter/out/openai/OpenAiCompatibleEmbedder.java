package net.yozmnews.evaluation.adapter.out.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.evaluation.EmbedderFailureKind;
import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import net.yozmnews.evaluation.domain.openai.EmbeddingApiKey;
import net.yozmnews.evaluation.domain.openai.OpenAiEmbeddingErrors;
import net.yozmnews.evaluation.domain.openai.OpenAiEmbeddingRequest;
import net.yozmnews.evaluation.domain.openai.OpenAiEmbeddingResponse;
import net.yozmnews.evaluation.domain.openai.OpenAiModelVersion;
import net.yozmnews.platform.CappedBody;
import org.jspecify.annotations.Nullable;

/**
 * OpenAI 호환 외부 임베딩 구현 — {@code POST {주소}/embeddings} · docs/specs/evaluation.md §4 · ADR-0090.
 * <p>
 * 텍스트를 한 요청의 입력 수로 나눠 차례로 보내고, 한 요청이라도 실패하면 그 실패가 전체 결과다. 판단(본문 · 응답 읽기 · 상태 갈래)은
 * {@code domain/openai} 가 하고, 이 클래스는 연결 · 마감 · 입출력 실패와 본문 상한 초과만 스스로 갈래로 바꾼다. 실패 문구는 고정 문구 ·
 * 상태 코드 · 예외 클래스 이름만이다 — 키 · 응답 본문 · 입력 텍스트를 담지 않는다.
 */
final class OpenAiCompatibleEmbedder implements ArticleEmbedder, AutoCloseable {

	/** 임베더 이름 */
	static final String NAME = "openai-compatible";

	/** 연결 마감 */
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** 요청 하나(헤더 · 본문 전부)의 마감 */
	static final Duration REQUEST_DEADLINE = Duration.ofSeconds(30);

	/** 응답 본문 상한 — 묶음 256 × 4,000 값 × 값당 32 바이트를 덮는다 */
	static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

	private static final String PATH = "/embeddings";

	private final HttpClient client;

	private final URI endpoint;

	private final String model;

	private final @Nullable EmbeddingApiKey key;

	private final boolean sendDimensions;

	private final String inputPrefix;

	private final int batchSize;

	private final Duration deadline;

	private final int maxResponseBytes;

	private final EmbedderInfo info;

	/**
	 * @param baseUrl 기본 주소 — 끝의 {@code /} 는 떼고 {@value #PATH} 를 붙인다
	 */
	OpenAiCompatibleEmbedder(URI baseUrl, String model, @Nullable EmbeddingApiKey key, int dimensions,
			boolean sendDimensions, String inputPrefix, int batchSize) {
		this(baseUrl, model, key, dimensions, sendDimensions, inputPrefix, batchSize, REQUEST_DEADLINE,
				MAX_RESPONSE_BYTES);
	}

	/** 시험이 마감 · 본문 상한을 줄인다 */
	OpenAiCompatibleEmbedder(URI baseUrl, String model, @Nullable EmbeddingApiKey key, int dimensions,
			boolean sendDimensions, String inputPrefix, int batchSize, Duration deadline, int maxResponseBytes) {
		String base = Objects.requireNonNull(baseUrl, "baseUrl").toString();
		this.endpoint = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + PATH);
		this.model = Objects.requireNonNull(model, "model");
		this.key = key;
		this.sendDimensions = sendDimensions;
		this.inputPrefix = Objects.requireNonNull(inputPrefix, "inputPrefix");
		if (batchSize < 1) {
			throw new IllegalArgumentException("한 요청의 입력 수는 1 이상이다: " + batchSize);
		}
		this.batchSize = batchSize;
		this.deadline = Objects.requireNonNull(deadline, "deadline");
		this.maxResponseBytes = maxResponseBytes;
		this.info = new EmbedderInfo(NAME,
				OpenAiModelVersion.of(model, dimensions, baseUrl, inputPrefix, sendDimensions), dimensions);
		this.client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY)
			.build();
	}

	@Override
	public EmbedderInfo info() {
		return this.info;
	}

	@Override
	public EmbeddingResult embed(List<String> texts) {
		Objects.requireNonNull(texts, "texts");
		List<Embedding> vectors = new ArrayList<>(texts.size());
		for (int from = 0; from < texts.size(); from += this.batchSize) {
			List<String> batch = texts.subList(from, Math.min(from + this.batchSize, texts.size()));
			EmbeddingResult result = request(batch);
			switch (result) {
				case Embeddings embeddings -> vectors.addAll(embeddings.vectors());
				case EmbeddingFailure failure -> {
					return failure;
				}
			}
		}
		return new Embeddings(vectors);
	}

	private EmbeddingResult request(List<String> batch) {
		List<String> inputs = batch.stream().map(text -> this.inputPrefix + text).toList();
		String body = OpenAiEmbeddingRequest.body(this.model, inputs,
				this.sendDimensions ? this.info.dimensions() : null);
		return switch (send(body)) {
			case Exchange.Broken broken -> new EmbeddingFailure(EmbedderFailureKind.TRANSIENT, broken.message());
			case Exchange.TooLarge tooLarge -> new EmbeddingFailure(EmbedderFailureKind.EMBEDDER_UNAVAILABLE,
					"외부 임베딩 응답 본문이 상한(" + this.maxResponseBytes + " 바이트)을 넘었다");
			case Exchange.Answered answered when answered.status() >= 200 && answered.status() <= 299 ->
				OpenAiEmbeddingResponse.read(answered.body(), batch.size());
			case Exchange.Answered answered -> new EmbeddingFailure(OpenAiEmbeddingErrors.classify(answered.status()),
					"외부 임베딩 요청이 실패했다 — 상태 " + answered.status());
		};
	}

	private Exchange send(String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(this.endpoint)
			.timeout(this.deadline)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (this.key != null) {
			builder.header("Authorization", this.key.authorization());
		}
		CompletableFuture<HttpResponse<CappedBody.Result>> future = this.client.sendAsync(builder.build(),
				info -> new CappedBody(this.maxResponseBytes));
		try {
			HttpResponse<CappedBody.Result> response = future.get(this.deadline.toNanos(), TimeUnit.NANOSECONDS);
			return switch (response.body()) {
				case CappedBody.Result.Overflow overflow -> new Exchange.TooLarge();
				case CappedBody.Result.Complete complete -> new Exchange.Answered(response.statusCode(), complete.text());
			};
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			return deadlinePassed();
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			return new Exchange.Broken("외부 임베딩 요청을 기다리다 중단됐다");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof HttpConnectTimeoutException) {
				return new Exchange.Broken("외부 임베딩 연결 마감(" + CONNECT_TIMEOUT.toSeconds() + "초)을 넘었다");
			}
			if (cause instanceof HttpTimeoutException) {
				return deadlinePassed();
			}
			if (cause instanceof IOException) {
				return new Exchange.Broken("외부 임베딩 API 와 주고받지 못했다 — " + cause.getClass().getSimpleName());
			}
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("외부 임베딩 요청 중 예상 못한 오류", cause);
		}
	}

	private Exchange.Broken deadlinePassed() {
		return new Exchange.Broken("외부 임베딩 응답 마감(" + this.deadline.toSeconds() + "초)을 넘었다");
	}

	@Override
	public void close() {
		this.client.close();
	}

	/** 요청 하나의 결말 */
	private sealed interface Exchange {

		/** 응답이 왔다 */
		record Answered(int status, String body) implements Exchange {

		}

		/** 응답 본문이 상한을 넘었다 */
		record TooLarge() implements Exchange {

		}

		/** 연결 · 마감 · 입출력으로 응답을 받지 못했다 — 문구는 고정 문구와 예외 클래스 이름만 */
		record Broken(String message) implements Exchange {

		}

	}

}
