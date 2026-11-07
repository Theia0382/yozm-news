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
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 사건 묶기 대기 반복 작업 — 임베더가 없으면 경고를 한 번 남기고 회차를 건너뛰어 기사가 대기에 남고, 있으면 표현 회차 · 배정 회차 ·
 * 재판정 회차 · 병합 회차를 차례로 돌린다. 실제 회차 · 대기 · PostgreSQL 을 쓰고, 작업은 시험이 임베더 제공자를 넣어 직접 만든다(작업 서버는 끈다).
 * <p>
 * 지키는 규칙: event-R7 · event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
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
	void withoutAnEmbedderTheJobWarnsOnceAndLeavesArticlesQueued(CapturedOutput output) {
		long article = enqueued();
		// 컨텍스트의 작업 빈도 기동 때 같은 경고를 남길 수 있다 — 이 시험이 만든 작업의 몫만 센다
		int before = warnings(output);
		IntakeArticlesJob job = new IntakeArticlesJob(this.round, this.assignment, this.rejudgement, this.merge, provider(null));

		job.run();
		job.run();

		assertThat(warnings(output)).isEqualTo(before + 1);
		assertThat(this.jdbc.sql("select count(*) from event_intake_queue where article_id = :article and lease_token is null")
			.param("article", article)
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThat(featureRows()).isZero();
	}

	@Test
	void withAnEmbedderTheJobRunsTheIntakeRoundAndThenTheAssignmentRound() {
		long article = enqueued();
		IntakeArticlesJob job = new IntakeArticlesJob(this.round, this.assignment, this.rejudgement, this.merge,
				provider(new FakeArticleEmbedder()));

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

	private long featureRows() {
		return this.jdbc.sql("select count(*) from article_match_features").query(Long.class).single();
	}

	private static int warnings(CapturedOutput output) {
		return output.getOut().split("임베더가 없어 사건 묶기 대기 기사를 처리하지 않는다 — 기사는 대기에 쌓인다", -1).length - 1;
	}

	private static ObjectProvider<ArticleEmbedder> provider(@Nullable ArticleEmbedder embedder) {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		if (embedder != null) {
			factory.registerSingleton("articleEmbedder", embedder);
		}
		return factory.getBeanProvider(ArticleEmbedder.class);
	}

}
