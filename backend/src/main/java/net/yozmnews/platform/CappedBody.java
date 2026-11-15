package net.yozmnews.platform;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/**
 * 상한까지 모으는 HTTP 응답 본문 수신자 — 외부 API 를 JDK {@code HttpClient} 로 부르는 어댑터가 함께 쓴다 · docs/specs/platform.md §3.
 * <p>
 * 요청은 {@link #sendAsync} 로 보낸다. 상한을 넘으면 넘김을 결과로 남기고, 응답이 넘겨진 뒤에 받기를 멈춘다(구독 취소) — 넘김과 동시에
 * 취소하면 JDK {@code HttpClient} 가 취소로 닫힌 연결을 입출력 오류로 응답에 먼저 알릴 수 있어, 넘김이 입출력 오류와 섞인다. 본문을 끝까지
 * 받거나 넘겨야 완료되므로, 부르는 쪽이 응답을 마감으로 기다리면 마감이 본문 읽기까지 덮는다. 본문은 UTF-8 로 읽는다.
 */
public final class CappedBody implements HttpResponse.BodySubscriber<CappedBody.Result> {

	private final int max;

	private final ByteArrayOutputStream received = new ByteArrayOutputStream();

	private final CompletableFuture<Result> result = new CompletableFuture<>();

	private volatile Flow.@Nullable Subscription subscription;

	private volatile boolean overflowed;

	/**
	 * @param max 본문 상한(바이트)
	 * @throws IllegalArgumentException 상한이 1 미만일 때
	 */
	CappedBody(int max) {
		if (max < 1) {
			throw new IllegalArgumentException("본문 상한은 1 이상이다: " + max);
		}
		this.max = max;
	}

	/**
	 * 요청을 보내고 응답 본문을 상한까지 받는다 — 상한을 넘기면 응답이 {@link Result.Overflow} 로 넘겨진 뒤 받기를 멈춘다.
	 * <p>
	 * 돌려주는 미래는 {@code client} 의 것 그대로다 — 부르는 쪽이 마감을 넘겨 취소하면 요청이 끊긴다.
	 * @param max 본문 상한(바이트)
	 * @throws IllegalArgumentException 상한이 1 미만일 때
	 */
	public static CompletableFuture<HttpResponse<Result>> sendAsync(HttpClient client, HttpRequest request, int max) {
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(request, "request");
		if (max < 1) {
			throw new IllegalArgumentException("본문 상한은 1 이상이다: " + max);
		}
		AtomicReference<@Nullable CappedBody> receiving = new AtomicReference<>();
		CompletableFuture<HttpResponse<Result>> response = client.sendAsync(request, info -> {
			CappedBody body = new CappedBody(max);
			receiving.set(body);
			return body;
		});
		response.whenComplete((answer, thrown) -> {
			CappedBody body = receiving.get();
			if (body != null) {
				body.stopIfOverflowed();
			}
		});
		return response;
	}

	/** 상한을 넘겼으면 받기를 멈춘다 — 응답이 넘겨진 뒤에 부른다 */
	void stopIfOverflowed() {
		Flow.Subscription active = this.subscription;
		if (this.overflowed && active != null) {
			active.cancel();
		}
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
				this.overflowed = true;
				this.result.complete(new Result.Overflow());
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

	/** 받은 결과 — 본문 전체 · 상한 넘김 */
	public sealed interface Result {

		/** 상한 안에서 다 받았다 */
		record Complete(String text) implements Result {

			public Complete {
				Objects.requireNonNull(text, "text");
			}

		}

		/** 상한을 넘어 받기를 멈췄다 */
		record Overflow() implements Result {

		}

	}

}
