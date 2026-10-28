package net.yozmnews.source.adapter.out.rss;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.fetch.AddressPolicy;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.FetchLimits;
import net.yozmnews.source.domain.fetch.NetworkRange;
import net.yozmnews.source.domain.fetch.UserAgent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 수집 구현을 설정으로 만든다 — 연락처가 없거나 설정이 틀리면 기동이 실패한다. Apache 클라이언트와 취소 스케줄러는
 * 컨텍스트 빈으로 두지 않는다: 스케줄러 빈은 Spring 의 기본 작업 스케줄러 자리를 차지하고, 클라이언트 빈은 내부 주소를
 * 막는 클라이언트를 다른 용도로 주입받게 한다. 둘은 {@link RssArticleSource} 가 소유하고 빈이 닫힐 때 닫는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CollectionProperties.class)
class RssArticleSourceConfiguration {

	static final String CONTACT_MISSING = "수집 연락처를 설정해야 한다 — yozmnews.collection.contact"
			+ "(환경 변수 YOZMNEWS_COLLECTION_CONTACT). 피드 운영자가 요청 주체에 닿을 이메일 주소나 http · https URL 이다";

	/**
	 * @throws IllegalStateException 연락처가 없거나 비었을 때, 형식이 틀렸을 때(원인은 {@code UserAgent} 의
	 * {@link IllegalArgumentException}) — 문구에 설정 키와 환경 변수 이름이 있다
	 */
	@Bean
	UserAgent feedUserAgent(CollectionProperties properties) {
		String contact = properties.contact();
		if (contact == null || contact.isBlank()) {
			throw new IllegalStateException(CONTACT_MISSING);
		}
		try {
			return new UserAgent(contact.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("yozmnews.collection.contact(환경 변수 YOZMNEWS_COLLECTION_CONTACT)가 틀렸다 — "
					+ ex.getMessage(), ex);
		}
	}

	/**
	 * 빈 항목은 건너뛴다 — 환경 변수가 비어 있으면 빈 목록이다.
	 * @throws IllegalStateException 항목이 CIDR 대역이 아닐 때(원인은 {@code NetworkRange} 의
	 * {@link IllegalArgumentException}) — 문구에 설정 키와 환경 변수 이름이 있다
	 */
	@Bean
	AddressPolicy feedAddressPolicy(CollectionProperties properties) {
		try {
			List<NetworkRange> allowed = Objects.requireNonNullElse(properties.allowedNetworks(), List.<String>of())
				.stream()
				.filter(entry -> !entry.isBlank())
				.map(NetworkRange::parse)
				.toList();
			return new AddressPolicy(allowed);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("yozmnews.collection.allowed-networks(환경 변수 "
					+ "YOZMNEWS_COLLECTION_ALLOWED_NETWORKS)가 틀렸다 — " + ex.getMessage(), ex);
		}
	}

	@Bean(destroyMethod = "close")
	ArticleSource articleSource(UserAgent feedUserAgent, AddressPolicy feedAddressPolicy) {
		return RssArticleSource.create(feedAddressPolicy, InstantSource.system(), FetchLimits.DEFAULT, feedUserAgent);
	}

}
