package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergePair;
import net.yozmnews.event.MergedEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 운영자 화면의 사건 조회 — 사건 · 배정 · 병합 판정 기록 행을 SQL 로 넣고, 합쳐진 사건 목록과 판정 원본의 쓰임(배정 · 병합 · 반영 안 됨)을
 * 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: event-R13 · event-R17 · event-R22
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventReviewFlowTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	@Autowired
	EventReview review;

	@Autowired
	JdbcClient jdbc;

	private long survivor;

	private long earlier;

	private long later;

	private long apart;

	private long criteriaId;

	@BeforeEach
	void startFromTheFixture() {
		EventArticles.clear(this.jdbc);
		this.criteriaId = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
		this.survivor = event("OPEN", null, null, 5);
		// 합친 시각이 늦은 쪽을 먼저 연다 — 사건 ID 순과 합친 시각 순이 어긋난다
		this.later = event("MERGED", this.survivor, "2026-11-06T12:00:00Z", 2);
		this.earlier = event("MERGED", this.survivor, "2026-11-06T06:00:00Z", 1);
		this.apart = event("OPEN", null, null, 1);
	}

	@AfterEach
	void clear() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void theCriteriaAreTheSettingsOfThisProcess() {
		assertThat(this.review.assignmentCriteria())
			.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.50));
	}

	@Test
	void mergedEventsAreListedByMergeTimeWithTheirPairProbability() {
		mergeRecord(this.later, this.survivor, 0.74, null);

		assertThat(this.review.mergedInto(new EventId(this.survivor))).containsExactly(
				new MergedEvent(new EventId(this.earlier), 1, T, T, T, Instant.parse("2026-11-06T06:00:00Z"), null),
				new MergedEvent(new EventId(this.later), 2, T, T, T, Instant.parse("2026-11-06T12:00:00Z"), 0.74));
		assertThat(this.review.mergedInto(new EventId(this.later))).isEmpty();
		assertThat(this.review.mergedInto(new EventId(this.apart))).isEmpty();
		assertThat(this.review.mergedInto(new EventId(999_999))).isEmpty();
	}

	@Test
	void aJudgementIsReadThroughTheRecordsThatApplyIt() {
		long attached = EventArticles.insert(this.jdbc, "판정으로 붙인 기사", null);
		assign(attached, this.survivor, DecidedBy.EVALUATOR, 101L);
		long formula = EventArticles.insert(this.jdbc, "산식으로 붙인 기사", null);
		assign(formula, this.survivor, DecidedBy.FORMULA, null);
		// 병합 판정 하나가 남긴 세 쌍 — 합쳐진 쌍, 떨어진 쌍, 둘 다 남는 사건으로 합쳐진 쌍
		mergeRecord(this.later, this.survivor, 0.74, 103L);
		mergeRecord(this.survivor, this.apart, 0.21, 103L);
		mergeRecord(this.later, this.earlier, 0.66, 103L);

		Map<EventJudgementId, JudgementUse> uses = this.review.judgementUses(
				List.of(new EventJudgementId(101), new EventJudgementId(103), new EventJudgementId(104)));

		assertThat(uses).containsOnly(
				Map.entry(new EventJudgementId(101),
						new JudgementUse.Assignment(new ArticleId(attached), new EventId(this.survivor),
								DecidedBy.EVALUATOR)),
				Map.entry(new EventJudgementId(103),
						new JudgementUse.Merge(List.of(
								new MergePair(new EventId(this.survivor), new EventId(this.later), 0.74,
										new EventId(this.survivor)),
								new MergePair(new EventId(this.survivor), new EventId(this.apart), 0.21, null),
								new MergePair(new EventId(this.later), new EventId(this.earlier), 0.66,
										new EventId(this.survivor))))));
		assertThat(this.review.judgementUses(List.of())).isEmpty();
	}

	@Test
	void anAssignmentUseFollowsTheArticleToItsEventNow() {
		long article = EventArticles.insert(this.jdbc, "재판정으로 옮긴 기사", null);
		assign(article, this.apart, DecidedBy.EVALUATOR, 101L);
		// 재판정이 다른 사건으로 옮기고 산식으로 다시 적었다 — 원본은 그대로다
		this.jdbc.sql("update event_articles set event_id = :event, decided_by = 'FORMULA' where article_id = :article")
			.param("event", this.survivor)
			.param("article", article)
			.update();

		assertThat(this.review.judgementUses(List.of(new EventJudgementId(101)))).containsExactly(
				Map.entry(new EventJudgementId(101), new JudgementUse.Assignment(new ArticleId(article),
						new EventId(this.survivor), DecidedBy.FORMULA)));
	}

	@Test
	void aJudgementPointedToByAnAssignmentAndAMergeRecordFailsLoudly() {
		long article = EventArticles.insert(this.jdbc, "어긋난 기록", null);
		assign(article, this.survivor, DecidedBy.EVALUATOR, 105L);
		mergeRecord(this.survivor, this.apart, 0.4, 105L);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.review.judgementUses(List.of(new EventJudgementId(105))));
	}

	@Test
	void aJudgementPointedToByTwoAssignmentsFailsLoudly() {
		long one = EventArticles.insert(this.jdbc, "어긋난 기록 하나", null);
		long two = EventArticles.insert(this.jdbc, "어긋난 기록 둘", null);
		assign(one, this.survivor, DecidedBy.EVALUATOR, 106L);
		assign(two, this.apart, DecidedBy.EVALUATOR, 106L);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.review.judgementUses(List.of(new EventJudgementId(106))));
	}

	/** 사건 하나 — 합쳐진 사건이면 남는 사건과 합친 시각 */
	private long event(String status, @Nullable Long mergedInto, @Nullable String closedAt, int articles) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, merged_into, closed_at)
				values (:status, 'model-a', cast('[1,0]' as halfvec), :articles, :t, :t, :t, :t, :mergedInto, :closedAt)
				returning id
				""")
			.param("status", status)
			.param("articles", articles)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.param("mergedInto", mergedInto, Types.BIGINT)
			.param("closedAt", (closedAt == null) ? null : Instant.parse(closedAt).atOffset(ZoneOffset.UTC),
					Types.TIMESTAMP_WITH_TIMEZONE)
			.query(Long.class)
			.single();
	}

	private void assign(long article, long event, DecidedBy by, @Nullable Long judgement) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at, judgement_id)
				values (:article, :event, :t, 0.6, :by, false, :criteria, :t, :judgement)
				""")
			.param("article", article)
			.param("event", event)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.param("by", by.name())
			.param("criteria", this.criteriaId)
			.param("judgement", judgement, Types.BIGINT)
			.update();
	}

	private void mergeRecord(long one, long other, double probability, @Nullable Long judgement) {
		this.jdbc.sql("""
				insert into event_merge_judgements (event_low, event_high, probability, judged_at, judgement_id)
				values (:low, :high, :probability, :t, :judgement)
				""")
			.param("low", Math.min(one, other))
			.param("high", Math.max(one, other))
			.param("probability", probability)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.param("judgement", judgement, Types.BIGINT)
			.update();
	}

}
