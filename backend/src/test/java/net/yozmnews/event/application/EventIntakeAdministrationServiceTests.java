package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.IntakeFillResult;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 사건 묶기 채우기 — 기간(지금 − 일수 × 24시간 이후, 그 시각 포함)에 처음 본 기사 가운데 배정이 없는 기사만 대기에 넣고, 이미 대기에
 * 있는 기사는 그대로 두며 세지 않는다. 일수는 1 ~ 30 이다. 실제 PostgreSQL 과 공개 조회로 본다.
 * <p>
 * 지키는 규칙: event-R19
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventIntakeAdministrationServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	@Autowired
	ArticleDirectory articles;

	@Autowired
	IntakeQueueRepository queue;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void fillsTheUnassignedArticlesFirstSeenWithinTheDays() {
		long boundary = article("2026-11-05T00:00:00Z");
		long recent = article("2026-11-05T12:00:00Z");
		long older = article("2026-11-04T23:59:59Z");
		long assigned = article("2026-11-05T06:00:00Z");
		long queued = article("2026-11-05T07:00:00Z");
		assign(assigned);
		this.jdbc.sql("insert into event_intake_queue (article_id, enqueued_at) values (:article, :t)")
			.param("article", queued)
			.param("t", Instant.parse("2026-11-05T07:00:00Z").atOffset(ZoneOffset.UTC))
			.update();

		IntakeFillResult result = service().fill(1);

		// 기간 안 넷(boundary · recent · assigned · queued) 가운데 새로 넣은 것은 boundary · recent
		assertThat(result).isEqualTo(new IntakeFillResult.Filled(4, 2));
		assertThat(this.jdbc.sql("select article_id from event_intake_queue order by article_id")
			.query(Long.class)
			.list()).containsExactly(boundary, recent, queued).doesNotContain(older, assigned);
		assertThat(this.jdbc.sql("select enqueued_at from event_intake_queue where article_id = :article")
			.param("article", recent)
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(NOW);
	}

	@Test
	void daysOutsideOneToThirtyDoNothing() {
		article("2026-11-05T12:00:00Z");

		assertThat(service().fill(0)).isEqualTo(new IntakeFillResult.DaysOutOfRange(30));
		assertThat(service().fill(31)).isEqualTo(new IntakeFillResult.DaysOutOfRange(30));
		assertThat(service().fill(30)).isEqualTo(new IntakeFillResult.Filled(1, 1));
		assertThat(this.jdbc.sql("select count(*) from event_intake_queue").query(Long.class).single()).isEqualTo(1);
	}

	private EventIntakeAdministrationService service() {
		return new EventIntakeAdministrationService(this.articles, this.queue, (InstantSource) () -> NOW);
	}

	private long article(String firstSeenAt) {
		long article = EventArticles.insert(this.jdbc, "채우기 시험 기사", null);
		this.jdbc.sql("update articles set first_seen_at = :seen where id = :article")
			.param("seen", Instant.parse(firstSeenAt).atOffset(ZoneOffset.UTC))
			.param("article", article)
			.update();
		return article;
	}

	private void assign(long article) {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at)
				values ('OPEN', 'model-a', '[1,0]', 1, now(), now(), now()) returning id
				""").query(Long.class).single();
		long criteria = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, decided_by, criteria_id, assigned_at)
				values (:article, :event, now(), 'FORMULA', :criteria, now())
				""").param("article", article).param("event", event).param("criteria", criteria).update();
	}

}
