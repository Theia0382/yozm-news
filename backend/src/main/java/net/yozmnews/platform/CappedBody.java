package net.yozmnews.platform;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.jspecify.annotations.Nullable;

/**
 * 상한까지 모으는 HTTP 응답 본문 수신자 — 외부 API 를 JDK {@code HttpClient} 로 부르는 어댑터가 함께 쓴다 · docs/specs/platform.md §3.
 * <p>
 * 상한을 넘으면 받기를 멈추고 넘김을 결과로 남긴다(입출력 오류와 섞지 않는다). 본문을 끝까지 받아야 완료되므로, 부르는 쪽이 이 수신자의 완료를
 * 마감으로 기다리면 마감이 본문 읽기까지 덮는다. 본문은 UTF-8 로 읽는다.
 */
public final class CappedBody implements HttpResponse.BodySubscriber<CappedBody.Result> {

	private final int max;

	private final ByteArrayOutputStream received = new ByteArrayOutputStream();

	private final CompletableFuture<Result> result = new CompletableFuture<>();

	private Flow.@Nullable Subscription subscription;

	/**
	 * @param max 본문 상한(바이트)
	 * @throws IllegalArgumentException 상한이 1 미만일 때
	 */
	public CappedBody(int max) {
		if (max < 1) {
			throw new IllegalArgumentException("본문 상한은 1 이상이다: " + max);
		}
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
