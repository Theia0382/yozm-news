package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.adapter.in.job.PollDueFeedsJob;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 같은 언론사의 요청 간격 — 작업 서버의 poll 간격을 운영 기본값(15초) 그대로 두고 잰다. 작업 서버는 예약 작업을 poll 간격 단위로 묶어
 * 시작하므로, 짧은 poll 간격에서는 작업 예약 시각의 분산만으로도 간격이 지켜진 것처럼 보인다. 판정은 WireMock 이 실제로 받은 시각이다.
 * <p>
 * 지키는 규칙: source-R18
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@TestPropertySource(properties = "yozmnews.collection.allowed-networks=127.0.0.1/32")
class SourceRequestSpacingTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	@Autowired
	SourceRegistration registration;

	@Autowired
	SourceOperations operations;

	@Autowired
	PollDueFeedsJob pollDueFeedsJob;

	@Autowired
	JobScheduler jobs;

	@Autowired
	JdbcClient jdbc;

	private TestFeeds feeds;

	@BeforeEach
	void reset() {
		this.feeds = new TestFeeds(this.registration, this.operations, this.jdbc, server);
		this.jobs.deleteRecurringJob("poll-due-feeds");
		this.jobs.deleteRecurringJob("feed-health-check");
		new RegistryRows(this.jdbc).deleteAll();
	}

	@Test
	void requestsOfOneSourceStartAtLeastTheSpacingApartWhileOtherSourcesDoNotWait() {
		this.feeds.source("/same-1.xml", "/same-2.xml");
		this.feeds.source("/other.xml");
		for (String path : List.of("/same-1.xml", "/same-2.xml", "/other.xml")) {
			server.stubFor(get(path).willReturn(TestFeeds.rss(path.substring(1, path.indexOf('.')))));
		}

		this.pollDueFeedsJob.run();
		await().atMost(Duration.ofSeconds(120))
			.until(() -> this.feeds.receivedAt(List.of("/same-1.xml", "/same-2.xml", "/other.xml")).size() == 3);

		List<Instant> same = this.feeds.receivedAt(List.of("/same-1.xml", "/same-2.xml"));
		Instant other = this.feeds.receivedAt(List.of("/other.xml")).getFirst();
		// 요청 시작 사이는 10초 이상이다. 수신 시각에는 연결 수립 같은 몇 ms 의 흔들림이 섞여 100ms 를 둔다 — 예약이 없으면 둘이 같은 ms 에 온다
		assertThat(Duration.between(same.get(0), same.get(1))).isGreaterThanOrEqualTo(Duration.ofMillis(9_900));
		assertThat(Duration.between(same.get(0), other).abs()).isLessThan(Duration.ofSeconds(10));
	}

}
