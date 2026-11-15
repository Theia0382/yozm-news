package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

/**
 * 상한까지 모으는 응답 본문 수신자 — 상한 안의 완료 · 상한 넘김(응답을 넘긴 뒤에 받기 취소) · 입출력 오류, 실제 HTTP 로 보내고 받기를 본다.
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
	void overflowsPastTheCapAndStopsOnlyAfterTheResponseIsHandedOver() throws Exception {
		CappedBody body = new CappedBody(4);
		Recorded subscription = subscribe(body);

		body.onNext(List.of(utf8("abc")));
		body.onNext(List.of(utf8("de")));

		// 넘김과 동시에 취소하면 HttpClient 가 닫힌 연결을 입출력 오류로 응답에 먼저 알릴 수 있다 — 응답을 넘긴 뒤에 멈춘다
		assertThat(result(body)).isEqualTo(new CappedBody.Result.Overflow());
		assertThat(subscription.cancelled).isFalse();
		body.onNext(List.of(utf8("f")));
		body.onComplete();
		assertThat(result(body)).isEqualTo(new CappedBody.Result.Overflow());

		body.stopIfOverflowed();
		assertThat(subscription.cancelled).isTrue();
	}

	@Test
	void aBodyWithinTheCapIsNotStopped() throws Exception {
		CappedBody body = new CappedBody(4);
		Recorded subscription = subscribe(body);

		body.onNext(List.of(utf8("abcd")));
		body.onComplete();
		body.stopIfOverflowed();

		assertThat(result(body)).isEqualTo(new CappedBody.Result.Complete("abcd"));
		assertThat(subscription.cancelled).isFalse();
	}

	@Test
	void sendsAndReceivesUpToTheCapOverHttp() throws Exception {
		String answer = "{\"answer\":\"0123456789\"}";
		int length = answer.getBytes(StandardCharsets.UTF_8).length;
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/answer", exchange -> {
			exchange.sendResponseHeaders(200, length);
			exchange.getResponseBody().write(answer.getBytes(StandardCharsets.UTF_8));
			exchange.close();
		});
		server.start();
		try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
			HttpRequest request = HttpRequest
				.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/answer"))
				.build();

			HttpResponse<CappedBody.Result> within = CappedBody.sendAsync(client, request, length)
				.get(5, TimeUnit.SECONDS);
			assertThat(within.statusCode()).isEqualTo(200);
			assertThat(within.body()).isEqualTo(new CappedBody.Result.Complete(answer));

			HttpResponse<CappedBody.Result> over = CappedBody.sendAsync(client, request, length - 1)
				.get(5, TimeUnit.SECONDS);
			assertThat(over.body()).isEqualTo(new CappedBody.Result.Overflow());
		}
		finally {
			server.stop(0);
		}
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
		assertThatIllegalArgumentException()
			.isThrownBy(() -> CappedBody.sendAsync(HttpClient.newHttpClient(), HttpRequest
				.newBuilder(URI.create("http://127.0.0.1:9/")).build(), 0));
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
