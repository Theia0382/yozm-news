package net.yozmnews.article.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.article.EntryBucket;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryRows;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.article.domain.EntryStatisticsRepository;
import net.yozmnews.source.FeedId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 공개 운영 API 의 엔트리 집계 — article 모듈 하나의 결선에서 시계 · 질의 · 칸 조립이 이어지는지 본다. 기대값은 손으로 셈한 수다.
 * <p>
 * 지키는 규칙: article-R13 · article-R14
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleAdministrationServiceTests {

	@Autowired
	ArticleAdministration administration;

	@Autowired
	EntryStatisticsRepository repository;

	@Autowired
	JdbcClient jdbc;

	EntryRows rows;

	/** 모듈 시험은 트랜잭션으로 감싸지 않으므로 앞뒤로 지운다 */
	@BeforeEach
	@AfterEach
	void deleteRows() {
		this.rows = new EntryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void countsTheEntriesOfTheSevenDaysBeforeNowPerFeedAndBucket() {
		this.rows.entry(this.rows.article(), 1, 7, "NEW", true, null, Instant.parse("2026-10-31T20:00:00Z"));
		this.rows.entry(this.rows.article(), 1, 7, "URL", false, null, Instant.parse("2026-10-31T21:00:00Z"));
		this.rows.entry(this.rows.article(), 1, 7, "TITLE", true, null, Instant.parse("2026-10-25T03:00:00Z"));
		ArticleAdministrationService service = new ArticleAdministrationService(this.repository,
				InstantSource.fixed(Instant.parse("2026-11-01T03:00:00Z")));

		EntryStatistics statistics = service.entryStatistics();

		assertThat(statistics.from()).isEqualTo(Instant.parse("2026-10-25T03:00:00Z"));
		assertThat(statistics.to()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
		assertThat(statistics.buckets()).hasSize(7)
			.startsWith(new EntryBucket(Instant.parse("2026-10-25T03:00:00Z"), Instant.parse("2026-10-26T03:00:00Z")));
		assertThat(statistics.feed(new FeedId(7)).buckets()).containsExactly(new EntryCounts(1, 1, 0, 1, 1, 0),
				EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE, EntryCounts.NONE,
				new EntryCounts(2, 1, 1, 0, 1, 0));
		assertThat(statistics.feed(new FeedId(7)).total()).isEqualTo(new EntryCounts(3, 2, 1, 1, 2, 0));
	}

	@Test
	void thePublicApiIsWiredToTheService() {
		assertThat(this.administration).isInstanceOf(ArticleAdministrationService.class);
	}

}
