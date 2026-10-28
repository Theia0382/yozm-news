package net.yozmnews.source.adapter.out.rss;

import net.yozmnews.source.domain.fetch.AddressPolicy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

/**
 * 수집용 Apache 클라이언트. 구성과 테스트가 같은 공장을 쓴다.
 */
final class FeedHttpClients {

	/**
	 * 연결 풀의 안전 상한 — 전체와 호스트당이 같다. 우리 쪽 자원을 지키는 값이고 동시성 정책이 아니다: 호스트당 제한을 두면
	 * 우리 쪽 대기가 마감을 먹어 언론사 탓이 아닌 실패가 피드에 쌓인다. 같은 호스트의 동시 수집은 수집 일정이 피한다
	 */
	static final int MAX_CONNECTIONS = 256;

	private FeedHttpClients() {
	}

	/**
	 * 리다이렉트 처리 끔(수집이 홉마다 직접 판정한다), 쿠키 끔, 인증 상태 없음, 자동 재시도 끔(한 번의 수집 안에서는 다시 보내지
	 * 않는다 — 다음 수집은 일정이 정한다), 시스템 프록시 설정을 읽지 않음(프록시를 거치면 연결 주소 검사가 무의미하다), 압축
	 * 요청 · 자동 해제는 기본대로 둔다.
	 */
	static CloseableHttpClient create(AddressPolicy policy) {
		return HttpClients.custom()
			.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
				.setDnsResolver(new PolicyDnsResolver(policy))
				.setMaxConnTotal(MAX_CONNECTIONS)
				.setMaxConnPerRoute(MAX_CONNECTIONS)
				.build())
			.setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
			.disableRedirectHandling()
			.disableCookieManagement()
			.disableAuthCaching()
			.disableAutomaticRetries()
			.build();
	}

}
