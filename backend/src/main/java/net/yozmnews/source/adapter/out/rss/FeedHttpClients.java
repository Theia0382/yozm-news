package net.yozmnews.source.adapter.out.rss;

import net.yozmnews.source.domain.fetch.AddressPolicy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

/**
 * 수집용 Apache 클라이언트 공장 — docs/specs/source.md §4 · source-R28 · source-R36
 */
final class FeedHttpClients {

	/** 연결 풀의 안전 상한 — 전체와 호스트당이 같다(ADR-0031) */
	static final int MAX_CONNECTIONS = 256;

	private FeedHttpClients() {
	}

	/**
	 * 수집용 클라이언트를 만든다.
	 * <p>
	 * 리다이렉트는 수집이 홉마다 직접 따라가므로 끈다. 압축 요청 · 자동 해제는 기본대로 둔다.
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
