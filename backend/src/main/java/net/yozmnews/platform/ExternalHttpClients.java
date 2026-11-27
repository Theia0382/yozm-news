package net.yozmnews.platform;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * 외부 API 를 부르는 JDK {@code HttpClient} 의 공용 구성 — docs/specs/platform.md §3 · platform-R135.
 * <p>
 * 외부 API 를 부르는 어댑터는 클라이언트를 이 구성으로만 만든다. 구성은 HTTP/1.1 · 리다이렉트를 따르지 않음 · 시스템 프록시를 읽지 않음이고, 연결
 * 마감은 부르는 쪽이 정한다. 요청 마감 · 응답 본문 상한도 부르는 쪽의 것이다.
 * <p>
 * HTTP/1.1 로 고정하는 이유 — HTTP/1.1 에서는 마감에 걸려 취소된 요청이 그 연결을 닫으므로 다음 요청이 새 연결로 간다. HTTP/2 에서는 목적지마다
 * 연결 하나에 요청을 모두 싣고, 요청이 취소돼도 연결을 남긴다. 그래서 네트워크 경로가 바뀌어 연결이 상대의 신호 없이 죽으면, 요청이 이어지는 동안
 * (쉬는 시간이 유휴 마감 30초에 닿지 않는 동안) 모든 요청이 그 연결로 가서 마감에 걸리고, 커널이 재전송을 포기할 때(기본 {@code tcp_retries2}
 * 15 에서 약 16분)까지 회복하지 못한다. 부르는 쪽은 돌려받은 빌더에서 {@code version} 을 다시 정하지 않는다.
 */
public final class ExternalHttpClients {

	private ExternalHttpClients() {
	}

	/**
	 * 공용 구성을 담은 새 빌더 — 부르는 쪽은 {@code build()} 만 한다.
	 * @param connectTimeout 연결 마감
	 */
	public static HttpClient.Builder builder(Duration connectTimeout) {
		Objects.requireNonNull(connectTimeout, "connectTimeout");
		return HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(HttpClient.Builder.NO_PROXY);
	}

}
