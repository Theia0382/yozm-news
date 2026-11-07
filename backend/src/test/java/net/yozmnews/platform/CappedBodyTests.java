package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

/**
 * 상한까지 모으는 응답 본문 수신자 — 상한 안의 완료 · 상한 넘김(받기 취소) · 입출력 오류를 본다.
 */
class CappedBodyTests {

	@Test
	void completesWithTheWholeBodyWithinTheCap() throws Exception {
		CappedBody body = new CappedBody(6);
		Recorded subscription = subscribe(body);

		body.onNext(List.of(utf8("가"), utf8("ab")));
		body.onComplete();

		assertThat(result(body)).isEqualTo(new CappedBody.Result.Complete("가ab"));
		assertThat(subscription.cancelled).isFalse();
	}

	@Test
	void overflowsAndCancelsPastTheCap() throws Exception {
		CappedBody body = new CappedBody(4);
		Recorded subscription = subscribe(body);

		body.onNext(List.of(utf8("abc")));
		body.onNext(List.of(utf8("de")));
		body.onNext(List.of(utf8("f")));
		body.onComplete();

		assertThat(result(body)).isEqualTo(new CappedBody.Result.Overflow());
		assertThat(subscription.cancelled).isTrue();
	}

	@Test
	void anErrorCompletesExceptionally() {
		CappedBody body = new CappedBody(4);
		subscribe(body);

		body.onError(new IOException("끊김"));

		CompletableFuture<CappedBody.Result> future = body.getBody().toCompletableFuture();
		assertThat(future).isCompletedExceptionally();
	}

	@Test
	void refusesACapBelowOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CappedBody(0));
	}

	private static Recorded subscribe(CappedBody body) {
		Recorded subscription = new Recorded();
		body.onSubscribe(subscription);
		return subscription;
	}

	private static CappedBody.Result result(CappedBody body) throws InterruptedException, ExecutionException {
		return body.getBody().toCompletableFuture().get();
	}

	private static ByteBuffer utf8(String text) {
		return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
	}

	private static final class Recorded implements Flow.Subscription {

		boolean cancelled;

		@Override
		public void request(long n) {
		}

		@Override
		public void cancel() {
			this.cancelled = true;
		}

	}

}
