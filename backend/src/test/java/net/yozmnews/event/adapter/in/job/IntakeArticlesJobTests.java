package net.yozmnews.event.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.adapter.out.fake.FakeArticleEmbedder;
import net.yozmnews.event.application.AssignmentRound;
import net.yozmnews.event.application.EventIntakeRound;
import net.yozmnews.event.application.MergeRound;
import net.yozmnews.event.application.RejudgementRound;
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
 * 사건 묶기 대기 반복 작업 — 생성자로 받은 임베더로 표현 회차 · 배정 회차 · 재판정 회차 · 병합 회차를 차례로 돌린다. 실제 회차 · 대기 ·
 * PostgreSQL 을 쓰고, 작업은 시험이 임베더를 넣어 직접 만든다(작업 서버는 끈다). 임베더 없이 뜨지 않음은 {@code IntakeArticlesJobWiringTests}
 * 가 본다.
 * <p>
 * 지키는 규칙: event-R7 · event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class IntakeArticlesJobTests {

	@Autowired
	EventIntakeRound round;

	@Autowired
	AssignmentRound assignment;

	@Autowired
	RejudgementRound rejudgement;

	@Autowired
	MergeRound merge;

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
	void withAnEmbedderTheJobRunsTheIntakeRoundAndThenTheAssignmentRound() {
		long article = enqueued();
		IntakeArticlesJob job = new IntakeArticlesJob(this.round, this.assignment, this.rejudgement, this.merge,
				new FakeArticleEmbedder());

		job.run();

		assertThat(this.jdbc.sql("select article_id from article_match_features").query(Long.class).list())
			.containsExactly(article);
		assertThat(this.jdbc.sql("select count(*) from event_intake_queue").query(Long.class).single()).isZero();
		// 같은 실행의 배정 회차가 표현을 쓴 기사를 배정했다 — 후보가 없어 새 사건
		assertThat(this.jdbc.sql("select article_id from event_articles").query(Long.class).list()).containsExactly(article);
		assertThat(this.jdbc.sql("select count(*) from event_assignment_queue").query(Long.class).single()).isZero();
		// 같은 실행의 병합 회차가 새 사건을 보고 표시했다 — 합칠 사건이 없다
		assertThat(this.jdbc.sql("select merge_checked_revision = revision from events").query(Boolean.class).list())
			.containsExactly(true);
	}

	private long enqueued() {
		long article = EventArticles.insert(this.jdbc, "한국은행 기준금리 동결", "한국은행이 기준금리를 연 3.25%로 동결했다.");
		this.queue.enqueue(new ArticleId(article), Instant.parse("2026-11-05T00:00:00Z"));
		return article;
	}

}
