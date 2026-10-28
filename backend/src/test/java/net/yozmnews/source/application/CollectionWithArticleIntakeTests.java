package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.adapter.in.job.PollDueFeedsJob;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 수집과 기사 저장을 함께 돌린 연결 수요 — 실제 작업 서버 · 실제 article 리스너 · 기본 연결 풀로 한 회차에 200건을 받는다. 한 소스 ·
 * 한 피드에 새 항목이 리스너 수보다 많은 경우를 넣는다(같은 소스의 리스너는 소스 단위 잠금 앞에 줄을 서고, 기다리는 동안 연결을
 * 잡는다). 모든 항목이 기사로 저장되고 남은 발행이 없고 연결 대기 시간 초과가 없어야 한다. 연결 대기 스레드 수의 최댓값은 기록으로
 * 남긴다 — 단언하지 않는다.
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@TestPropertySource(properties = "yozmnews.collection.allowed-networks=127.0.0.1/32")
@ExtendWith(OutputCaptureExtension.class)
class CollectionWithArticleIntakeTests {

	private static final Logger logger = LoggerFactory.getLogger(CollectionWithArticleIntakeTests.class);

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

	@Autowired
	DataSource dataSource;

	private TestFeeds feeds;

	@BeforeEach
	void reset() {
		this.feeds = new TestFeeds(this.registration, this.operations, this.jdbc, server);
		this.jobs.deleteRecurringJob("poll-due-feeds");
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
		this.jdbc.sql("delete from event_publication").update();
		new RegistryRows(this.jdbc).deleteAll();
	}

	@Test
	void everyFetchedItemIsStoredWithoutConnectionTimeouts(CapturedOutput output) throws SQLException {
		List<Long> feedIds = new ArrayList<>();
		for (int source = 0; source < 8; source++) {
			String path = "/load-" + source + ".xml";
			int items = (source == 0) ? 60 : 20;
			String[] guids = new String[items];
			for (int item = 0; item < items; item++) {
				guids[item] = "load-" + source + "-" + item;
			}
			server.stubFor(get(path).willReturn(TestFeeds.rss(guids)));
			feedIds.add(this.feeds.source(path).getFirst());
		}
		List<Long> sourceIds = this.jdbc.sql("select source_id from feeds where id in (:feeds)")
			.param("feeds", feedIds)
			.query(Long.class)
			.list();
		HikariPoolMXBean pool = this.dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
		AtomicInteger maxAwaiting = new AtomicInteger();
		AtomicInteger samples = new AtomicInteger();
		AtomicInteger waitingSamples = new AtomicInteger();
		AtomicLong awaitingSum = new AtomicLong();
		ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
		sampler.scheduleAtFixedRate(() -> {
			int awaiting = pool.getThreadsAwaitingConnection();
			maxAwaiting.accumulateAndGet(awaiting, Math::max);
			samples.incrementAndGet();
			awaitingSum.addAndGet(awaiting);
			if (awaiting > 0) {
				waitingSamples.incrementAndGet();
			}
		}, 0, 50, TimeUnit.MILLISECONDS);
		Instant start = Instant.now();
		try {
			this.pollDueFeedsJob.run();
			await().atMost(Duration.ofSeconds(120))
				.pollInterval(Duration.ofMillis(200))
				.until(() -> count("select count(*) from articles where source_id in (:ids)", sourceIds) == 200
						&& count("select count(*) from article_feed_entries where feed_id in (:ids)", feedIds) == 200
						&& this.jdbc.sql("select count(*) from event_publication").query(Integer.class).single() == 0);
		}
		finally {
			sampler.shutdownNow();
		}
		logger.info("수집 · 기사 저장 연결 측정 — 항목 200건, 소요 {}ms, 풀 최대 {}, 연결 대기 스레드 최댓값 {} · 평균 {}, 대기가 있던 표본 {}/{}",
				Duration.between(start, Instant.now()).toMillis(),
				this.dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize(), maxAwaiting.get(),
				String.format("%.2f", (double) awaitingSum.get() / Math.max(1, samples.get())), waitingSamples.get(),
				samples.get());

		assertThat(output).contains("수집 · 기사 저장 연결 측정");
		assertThat(output).doesNotContain("SQLTransientConnectionException");
	}

	private int count(String sql, List<Long> ids) {
		return this.jdbc.sql(sql).param("ids", ids).query(Integer.class).single();
	}

}
