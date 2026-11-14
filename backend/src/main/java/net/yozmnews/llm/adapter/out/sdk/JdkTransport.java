package net.yozmnews.llm.adapter.out.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.yozmnews.llm.adapter.out.sdk.TransportFailure.Kind;
import net.yozmnews.platform.CappedBody;
import org.jspecify.annotations.Nullable;

/**
 * 공급자로 가는 HTTP 전송의 정본 — docs/specs/llm.md §4 · llm-R7 · R13 · R14.
 * <p>
 * 리다이렉트를 따르지 않고, 프록시를 쓰지 않고, 응답 본문을 상한까지만 받고, 마감을 헤더와 본문 전부에 건다. 응답을 넘기지 못하면
 * {@link TransportFailure} 다 — 문구에 주소 · 헤더 · 본문 · 원인 예외의 문구를 담지 않는다. 닫은 뒤의 요청은 보내지 않는다.
 */
final class JdkTransport implements AutoCloseable {

	/** 연결 마감 */
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	/** 요청 하나(헤더 · 본문 전부)의 마감 */
	static final Duration REQUEST_DEADLINE = Duration.ofSeconds(15);

	/** 응답 본문 상한 */
	static final int MAX_RESPONSE_BYTES = 1024 * 1024;

	/** JDK 클라이언트가 스스로 정하는 헤더 — 넘겨받아도 싣지 않는다 */
	private static final Set<String> MANAGED_HEADERS = Set.of("connection", "content-length", "expect", "host",
			"upgrade");

	private final HttpClient client;

	private final Duration deadline;

	private final int maxResponseBytes;

	private final AtomicBoolean closed = new AtomicBoolean();

	JdkTransport() {
		this(REQUEST_DEADLINE, MAX_RESPONSE_BYTES);
	}

	/** 시험이 마감 · 본문 상한을 줄인다 */
	JdkTransport(Duration deadline, int maxResponseBytes) {
		this.deadline = Objects.requireNonNull(deadline, "deadline");
		this.maxResponseBytes = maxResponseBytes;
		this.client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY)
			.build();
	}

	/**
	 * 요청 하나를 보내고 응답을 기다린다.
	 * @throws TransportFailure 응답을 넘기지 못했을 때
	 */
	Response send(Request request) {
		CompletableFuture<HttpResponse<CappedBody.Result>> future = start(request);
		try {
			return response(future.get(this.deadline.toNanos(), TimeUnit.NANOSECONDS));
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			throw deadlinePassed();
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new TransportFailure(Kind.INTERRUPTED, "LLM 공급자의 응답을 기다리다 중단됐다");
		}
		catch (ExecutionException ex) {
			throw failure(ex.getCause());
		}
	}

	/** {@link #send} 의 비동기 꼴 — 실패는 {@link TransportFailure} 로 끝난 미래다 */
	CompletableFuture<Response> sendAsync(Request request) {
		CompletableFuture<HttpResponse<CappedBody.Result>> future;
		try {
			future = start(request);
		}
		catch (TransportFailure ex) {
			return CompletableFuture.failedFuture(ex);
		}
		return future.orTimeout(this.deadline.toNanos(), TimeUnit.NANOSECONDS).handle((response, thrown) -> {
			if (thrown != null) {
				throw failure((thrown instanceof CompletionException && thrown.getCause() != null) ? thrown.getCause()
						: thrown);
			}
			return response(response);
		});
	}

	private CompletableFuture<HttpResponse<CappedBody.Result>> start(Request request) {
		Objects.requireNonNull(request, "request");
		if (this.closed.get()) {
			throw new TransportFailure(Kind.CLOSED, "닫은 LLM 공급자 클라이언트에 요청했다");
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
			.timeout(this.deadline)
			.method(request.method(), (request.body() != null) ? HttpRequest.BodyPublishers.ofByteArray(request.body())
					: HttpRequest.BodyPublishers.noBody());
		request.headers().forEach((name, values) -> {
			if (!MANAGED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
				values.forEach(value -> builder.header(name, value));
			}
		});
		return this.client.sendAsync(builder.build(), info -> new CappedBody(this.maxResponseBytes));
	}

	private Response response(HttpResponse<CappedBody.Result> response) {
		return switch (response.body()) {
			case CappedBody.Result.Overflow overflow -> throw new TransportFailure(Kind.TOO_LARGE,
					"LLM 공급자의 응답 본문이 상한(" + this.maxResponseBytes + " 바이트)을 넘었다");
			case CappedBody.Result.Complete complete ->
				new Response(response.statusCode(), response.headers().map(), complete.text());
		};
	}

	private RuntimeException failure(@Nullable Throwable cause) {
		if (cause instanceof TransportFailure failure) {
			return failure;
		}
		if (cause instanceof HttpConnectTimeoutException) {
			return new TransportFailure(Kind.CONNECT, "LLM 공급자 연결 마감(" + CONNECT_TIMEOUT.toSeconds() + "초)을 넘었다");
		}
		if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException
				|| cause instanceof CancellationException) {
			return deadlinePassed();
		}
		if (cause instanceof IOException) {
			return new TransportFailure(Kind.IO, "LLM 공급자와 주고받지 못했다 — " + cause.getClass().getSimpleName());
		}
		if (cause instanceof RuntimeException runtime) {
			return runtime;
		}
		if (cause instanceof Error error) {
			throw error;
		}
		return new IllegalStateException("LLM 공급자 요청 중 예상 못한 오류 — "
				+ ((cause != null) ? cause.getClass().getSimpleName() : "원인 없음"));
	}

	private TransportFailure deadlinePassed() {
		return new TransportFailure(Kind.DEADLINE, "LLM 공급자 응답 마감(" + this.deadline.toMillis() + "ms)을 넘었다");
	}

	/** 연결 · 스레드를 푼다 — 하고 있던 요청을 기다리지 않는다. 여러 번 불러도 된다 */
	@Override
	public void close() {
		if (this.closed.compareAndSet(false, true)) {
			this.client.shutdownNow();
		}
	}

	/**
	 * 보낼 요청.
	 *
	 * @param body 본문 — 없으면 null
	 */
	record Request(String method, URI uri, Map<String, List<String>> headers, byte @Nullable [] body) {

		Request {
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(headers, "headers");
		}

	}

	/** 받은 응답 — 본문은 상한 안에서 끝까지 받은 것이다 */
	record Response(int status, Map<String, List<String>> headers, String body) {

	}

}
