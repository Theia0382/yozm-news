package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.yozmnews.RoundRunRows;
import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventEmbeddings;
import net.yozmnews.event.EventProcessing;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import net.yozmnews.platform.RoundBatch;
import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.platform.RoundRunner;
import net.yozmnews.platform.RoundSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 쪽 처리 현황 — 회차 넷의 남은 수 · 미룬 수와 회차 기록의 최근 합을 실제 PostgreSQL 에서 센다.
 * <p>
 * 지금 시각은 실제 시계다 — 미룬 행은 하루 뒤로, 지금 가져올 수 있는 행은 하루 앞으로 둔다. 시험 문장은 지어낸 것이다.
 * <p>
 * 지키는 규칙: event-R6 · event-R13 · event-R16 · event-R18
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventProcessingFlowTests {

	private static final Instant PUBLISHED = Instant.parse("2026-11-05T00:00:00Z");

	/** 설정의 기준과 확률 기준만 다른 기준 — 이것으로 배정한 행은 지금 기준이 아니다 */
	private static final AssignmentCriteria OTHER_CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55,
			0.40, 0.45);

	@Autowired
	EventProcessing processing;

	@Autowired
	RoundRunner runner;

	@Autowired
	IntakeQueueRepository intake;

	@Autowired
	AssignmentQueueRepository assignmentQueue;

	@Autowired
	MatchFeaturesRepository features;

	@Autowired
	EventRepository events;

	@Autowired
	EventArticleRepository assignments;

	@Autowired
	CriteriaRepository criteriaHistory;

	@Autowired
	AssignmentSettings settings;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
		RoundRunRows.clear(this.jdbc);
	}

	@Test
	void nothingWaitingIsZeroForEveryRoundInOrder() {
		assertThat(this.processing.rounds()).extracting(RoundQueue::round, RoundQueue::waiting, RoundQueue::deferred)
			.containsExactly(tuple("event-features", 0L, 0L), tuple("event-assignment", 0L, 0L),
					tuple("event-rejudgement", 0L, 0L), tuple("event-merge", 0L, 0L));
	}

	@Test
	void queuesAreCountedWithTheRowsWhoseTimeHasNotCome() {
		this.intake.enqueue(new ArticleId(EventArticles.insert(this.jdbc, "대기 하나", null)), PUBLISHED);
		this.intake.enqueue(new ArticleId(EventArticles.insert(this.jdbc, "대기 둘", null)), PUBLISHED);
		long later = EventArticles.insert(this.jdbc, "배정 하나", null);
		for (long article : List.of(later, EventArticles.insert(this.jdbc, "배정 둘", null),
				EventArticles.insert(this.jdbc, "배정 셋", null))) {
			this.assignmentQueue.enqueue(new ArticleId(article), PUBLISHED);
		}
		this.jdbc.sql("update event_assignment_queue set available_at = now() + interval '1 day' where article_id = :a")
			.param("a", later)
			.update();

		assertThat(this.processing.rounds()).extracting(RoundQueue::round, RoundQueue::waiting, RoundQueue::deferred)
			.startsWith(tuple("event-features", 2L, 0L), tuple("event-assignment", 3L, 1L));
	}

	@Test
	void withoutTheCurrentCriteriaEveryAssignmentAndOpenEventIsLeftAndNothingIsRegistered() {
		assigned(3);
		long criteriaRows = criteriaRows();

		List<RoundQueue> rounds = this.processing.rounds();

		assertThat(rounds).extracting(RoundQueue::round, RoundQueue::waiting, RoundQueue::deferred)
			.endsWith(tuple("event-rejudgement", 3L, 0L), tuple("event-merge", 3L, 0L));
		assertThat(criteriaRows()).isEqualTo(criteriaRows);
	}

	@Test
	void underTheCurrentCriteriaOnlyOtherAssignmentsAndUncheckedEventsAreLeft() {
		List<Long> articles = assigned(3);
		long current = this.criteriaHistory.register(this.settings.criteria(), PUBLISHED);
		this.jdbc.sql("update event_articles set criteria_id = :c where article_id = :a")
			.param("c", current)
			.param("a", articles.get(0))
			.update();
		// 사건 셋 — 하나는 지금 기준으로 확인했고, 하나는 미뤄 하루 뒤에 다시 본다
		List<Long> eventIds = this.jdbc.sql("select id from events order by id").query(Long.class).list();
		this.jdbc.sql("update events set merge_checked_revision = revision, merge_checked_criteria_id = :c where id = :e")
			.param("c", current)
			.param("e", eventIds.get(0))
			.update();
		this.jdbc.sql("update events set merge_retry_at = now() + interval '1 day', merge_deferrals = 1 where id = :e")
			.param("e", eventIds.get(1))
			.update();

		assertThat(this.processing.rounds()).extracting(RoundQueue::round, RoundQueue::waiting, RoundQueue::deferred)
			.endsWith(tuple("event-rejudgement", 2L, 0L), tuple("event-merge", 2L, 1L));
	}

	@Test
	void theRecentTotalsComeFromTheRoundRecord() {
		this.runner.run("event-merge", () -> RoundBatch.finished(3, 16));
		this.runner.run("event-merge", () -> RoundBatch.finished(2, 16));

		RoundQueue merge = this.processing.rounds().get(3);

		assertThat(merge.recent().handled()).isEqualTo(5);
		assertThat(merge.recent().runs()).isEqualTo(2);
		assertThat(merge.recent().last()).isNotNull()
			.extracting(RoundSummary.LastRound::batches, RoundSummary.LastRound::handled, RoundSummary.LastRound::end)
			.containsExactly(1, 2, RoundEnd.DRAINED);
		assertThat(this.processing.rounds().get(0).recent().last()).isNull();
	}

	/** 서로 떨어진 기사 {@code count} 개를 배정해 사건을 하나씩 연다 */
	private List<Long> assigned(int count) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			long article = EventArticles.insert(this.jdbc, "현황 시험 기사 " + i, null);
			float[] axis = new float[count];
			axis[i] = 1;
			this.jdbc.sql("""
					insert into article_match_features (article_id, model_version, embedding, entities, created_at)
					values (:article, 'model-a', cast(:vector as vector), '{}', :now)
					""")
				.param("article", article)
				.param("vector", Arrays.toString(axis).replace(" ", ""))
				.param("now", PUBLISHED.atOffset(ZoneOffset.UTC))
				.update();
			this.assignmentQueue.enqueue(new ArticleId(article), PUBLISHED);
			ids.add(article);
		}
		new AssignmentRound(this.assignmentQueue, this.intake, this.features, this.events, this.assignments,
				this.criteriaHistory, OTHER_CRITERIA,
				this.articles, query -> new MembershipJudgement.Judged(null, Map.of()),
				EventEmbeddings.modelOnly("model-a"), this.transaction, TestClocks.fixed(PUBLISHED), this.runner)
			.run();
		return ids;
	}

	private long criteriaRows() {
		return this.jdbc.sql("select count(*) from event_assignment_criteria").query(Long.class).single();
	}

}
