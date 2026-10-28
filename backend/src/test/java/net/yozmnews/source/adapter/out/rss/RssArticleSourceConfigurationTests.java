package net.yozmnews.source.adapter.out.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import net.yozmnews.source.domain.fetch.AddressPolicy;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.UserAgent;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 수집 구성 — 연락처가 없거나 설정이 틀리면 기동이 실패한다.
 */
class RssArticleSourceConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(RssArticleSourceConfiguration.class);

	@Test
	void startsWithAContact() {
		this.runner.withPropertyValues("yozmnews.collection.contact=ops@example.org").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(ArticleSource.class)).isInstanceOf(RssArticleSource.class);
			assertThat(context.getBean(UserAgent.class).headerValue())
				.isEqualTo("YozmNews (+https://github.com/Theia0382/yozm-news; ops@example.org)");
			assertThat(context).doesNotHaveBean(CloseableHttpClient.class);
			assertThat(context).doesNotHaveBean(ScheduledExecutorService.class);
		});
	}

	@Test
	void failsWithoutAContact() {
		this.runner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("yozmnews.collection.contact")
				.hasMessageContaining("YOZMNEWS_COLLECTION_CONTACT");
		});
	}

	@Test
	void failsWithABlankContact() {
		this.runner.withPropertyValues("yozmnews.collection.contact=   ")
			.run(context -> assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("YOZMNEWS_COLLECTION_CONTACT"));
	}

	@Test
	void failsWithAnInvalidContact() {
		this.runner.withPropertyValues("yozmnews.collection.contact=not a contact")
			.run(context -> {
				assertThat(context.getStartupFailure()).hasStackTraceContaining("YOZMNEWS_COLLECTION_CONTACT");
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("연락처");
			});
	}

	@Test
	void readsAllowedNetworks() {
		this.runner
			.withPropertyValues("yozmnews.collection.contact=ops@example.org",
					"yozmnews.collection.allowed-networks=10.0.0.0/8,192.168.1.0/24")
			.run(context -> {
				AddressPolicy policy = context.getBean(AddressPolicy.class);
				assertThat(policy.firstBlocked(List.of(address("10.1.2.3"), address("192.168.1.9")))).isEmpty();
				assertThat(policy.firstBlocked(List.of(address("192.168.2.1")))).contains(address("192.168.2.1"));
				assertThat(policy.firstBlocked(List.of(address("127.0.0.1")))).contains(address("127.0.0.1"));
			});
	}

	@Test
	void emptyAllowedNetworksBlockEveryInternalAddress() {
		this.runner
			.withPropertyValues("yozmnews.collection.contact=ops@example.org", "yozmnews.collection.allowed-networks=")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(AddressPolicy.class).firstBlocked(List.of(address("10.1.2.3"))))
					.contains(address("10.1.2.3"));
			});
	}

	/** 환경 변수 끝의 쉼표 · 공백 항목은 건너뛴다 — 형식이 틀린 항목만 기동을 막는다 */
	@Test
	void skipsBlankAllowedNetworkEntries() {
		this.runner
			.withPropertyValues("yozmnews.collection.contact=ops@example.org",
					"yozmnews.collection.allowed-networks=10.0.0.0/8, ,")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(AddressPolicy.class).firstBlocked(List.of(address("10.1.2.3")))).isEmpty();
			});
	}

	@Test
	void failsWithAnInvalidAllowedNetwork() {
		this.runner
			.withPropertyValues("yozmnews.collection.contact=ops@example.org",
					"yozmnews.collection.allowed-networks=10.0.0.1/8")
			.run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("CIDR 대역이 아니다: 10.0.0.1/8")
				.hasStackTraceContaining("YOZMNEWS_COLLECTION_ALLOWED_NETWORKS")
				.rootCause()
				.isInstanceOf(IllegalArgumentException.class));
	}

	private static InetAddress address(String literal) {
		return InetAddress.ofLiteral(literal);
	}

}
