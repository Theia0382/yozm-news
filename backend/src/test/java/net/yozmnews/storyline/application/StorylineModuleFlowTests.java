package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기사 삭제가 스토리라인까지 따라오는 한 흐름 — {@code ArticlesRemoved} 를 {@code event} 가 받아 기사 하나뿐인 사건을 지우고
 * {@code EventsChanged} 를 발행하면, {@code storyline} 리스너가 그 사건을 잇기 대기에 넣고, 회차가 소속을 지운다. 리스너는 실제
 * 비동기 리스너다(작업 서버는 끄고 회차는 시험이 부른다).
 * <p>
 * 지키는 규칙: storyline-R1 · storyline-R6 · event-R8 · event-R23
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineModuleFlowTests {

	@Autowired
	LinkingRound round;

	@Autowired
	LinkingQueueRepository queue;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void removingTheOnlyArticleOfAnEventTakesItOutOfItsStoryline() {
		long e1 = StorylineRows.event(this.jdbc, "OPEN", "module-m", "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("금리", 1));
		long e2 = StorylineRows.event(this.jdbc, "OPEN", "module-m", "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of("금리", 1));
		assign(7_700_001L, e1);
		assign(7_700_002L, e2);
		this.queue.enqueue(e1, Instant.parse("2026-11-05T00:00:00Z"), Instant.now());
		this.round.run();
		long storyline = this.jdbc.sql("select id from storylines where model_version = 'module-m'")
			.query(Long.class)
			.single();
		assertThat(members(storyline)).containsExactly(e1, e2);

		this.transaction.executeWithoutResult(
				status -> this.events.publishEvent(new ArticlesRemoved(List.of(new ArticleId(7_700_001L)))));

		// event 가 e1 을 지우고 알리면 storyline 리스너가 대기에 넣는다 — 순서 키 없음(지워진 사건)
		await().atMost(Duration.ofSeconds(30))
			.until(() -> this.jdbc.sql("select count(*) from storyline_linking_queue where event_id = :id and order_at is null")
				.param("id", e1)
				.query(Long.class)
				.single() == 1);
		assertThat(this.jdbc.sql("select count(*) from events where id = :id").param("id", e1).query(Long.class).single())
			.isZero();

		this.round.run();

		assertThat(members(storyline)).containsExactly(e2);
	}

	private void assign(long article, long event) {
		long criteria = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now())
				on conflict (window_seconds, upper, lower, store, probability) do update set created_at = excluded.created_at
				returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, '2026-11-05T00:00:00Z', null, 'FORMULA', true, :criteria, '2026-11-05T00:00:00Z')
				""").param("article", article).param("event", event).param("criteria", criteria).update();
	}

	private List<Long> members(long storyline) {
		return this.jdbc.sql("select event_id from storyline_members where storyline_id = :id order by event_id")
			.param("id", storyline)
			.query(Long.class)
			.list();
	}

}
