package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.PublishedSpan;
import net.yozmnews.event.domain.event.RejudgeTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 배정 저장소 — 배정과 후보의 쓰기(있으면 그대로), 재판정 대상(기준이 다른 배정 · 기사 ID 순 · 사라진 사건의 후보 제외),
 * 다시 적기 · 기준만 적기, 남은 배정의 발행 시각 범위, 잠금 · 지우기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: event-R12 · event-R18
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEventArticleRepository.class })
class JdbcEventArticleRepositoryTests {

	private static final Instant PUBLISHED = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant ASSIGNED = Instant.parse("2026-11-05T01:00:00Z");

	@Autowired
	JdbcEventArticleRepository repository;

	@Autowired
	JdbcClient jdbc;

	long event;

	long other;

	long oldCriteria;

	long criteria;

	@BeforeEach
	void events() {
		this.event = event(3);
		this.other = event(1);
		this.oldCriteria = criteria(0.80);
		this.criteria = criteria(0.85);
	}

	@Test
	void insertsTheAssignmentAndItsCandidatesOnce() {
		EventArticle row = new EventArticle(new ArticleId(1), this.event, PUBLISHED, 0.72, DecidedBy.EVALUATOR,
				false, this.criteria, ASSIGNED, new EventJudgementId(9));

		boolean inserted = this.repository.insertIfAbsent(row, List.of(new StoredCandidate(this.event, 0.72, 0.6),
				new StoredCandidate(this.other, 0.45, null)));
		boolean again = this.repository.insertIfAbsent(new EventArticle(new ArticleId(1), this.other, PUBLISHED, null,
				DecidedBy.FORMULA, false, this.criteria, ASSIGNED, null), List.of(new StoredCandidate(this.other, 0.9, null)));

		assertThat(inserted).isTrue();
		assertThat(again).isFalse();
		assertThat(this.repository.exists(new ArticleId(1))).isTrue();
		assertThat(this.repository.exists(new ArticleId(2))).isFalse();
		assertThat(this.repository.lockByArticles(List.of(new ArticleId(1)))).containsExactly(row);
		assertThat(candidates(1)).containsExactly(new StoredCandidate(this.event, 0.72, 0.6),
				new StoredCandidate(this.other, 0.45, null));
	}

	@Test
	void anAssignmentWithoutScoreKeepsNull() {
		EventArticle row = new EventArticle(new ArticleId(3), this.event, PUBLISHED, null, DecidedBy.JUDGEMENT_FAILED,
				true, this.criteria, ASSIGNED, null);
		this.repository.insertIfAbsent(row, List.of());

		assertThat(this.repository.lockByArticles(List.of(new ArticleId(3)))).containsExactly(row);
	}

	@Test
	void rejudgeTargetsAreAssignmentsOfOtherCriteriaInArticleOrder() {
		long newer = criteria(0.90);
		assign(13, this.event, this.oldCriteria, List.of(new StoredCandidate(this.event, 0.70, 0.6),
				new StoredCandidate(this.other, 0.60, null), new StoredCandidate(this.other + 1_000, 0.65, 0.9)));
		assign(11, this.other, newer, List.of());
		assign(12, this.event, this.criteria, List.of(new StoredCandidate(this.event, 0.81, null)));
		assign(14, this.event, this.oldCriteria, List.of());

		List<RejudgeTarget> targets = this.repository.lockRejudgeTargets(this.criteria, 2);

		assertThat(targets).extracting(target -> target.assignment().articleId())
			.containsExactly(new ArticleId(11), new ArticleId(13));
		assertThat(targets.get(0).stored()).isEmpty();
		// 사라진 사건(other + 1000)의 후보는 빠진다
		assertThat(targets.get(1).stored()).containsExactly(new StoredCandidate(this.event, 0.70, 0.6),
				new StoredCandidate(this.other, 0.60, null));
		assertThat(this.repository.lockRejudgeTargets(this.criteria, 10)).extracting(t -> t.assignment().articleId())
			.containsExactly(new ArticleId(11), new ArticleId(13), new ArticleId(14));
	}

	@Test
	void reassignRewritesTheAssignmentAndKeepRewritesOnlyTheCriteria() {
		assign(21, this.event, this.oldCriteria, List.of());
		assign(22, this.event, this.oldCriteria, List.of());

		this.repository.reassign(new ArticleId(21), this.other, null, DecidedBy.EVALUATOR, true, this.criteria,
				Instant.parse("2026-11-06T00:00:00Z"));
		this.repository.keep(new ArticleId(22), this.criteria);

		assertThat(this.repository.lockByArticles(List.of(new ArticleId(22), new ArticleId(21)))).containsExactly(
				new EventArticle(new ArticleId(21), this.other, PUBLISHED, null, DecidedBy.EVALUATOR, true, this.criteria,
						Instant.parse("2026-11-06T00:00:00Z"), null),
				new EventArticle(new ArticleId(22), this.event, PUBLISHED, 0.5, DecidedBy.FORMULA, false, this.criteria,
						ASSIGNED, null));
	}

	@Test
	void thePublishedSpanCoversTheRemainingAssignmentsOfTheEvent() {
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(41), this.event, Instant.parse("2026-11-05T03:00:00Z"),
				null, DecidedBy.FORMULA, false, this.criteria, ASSIGNED, null), List.of());
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(42), this.event, Instant.parse("2026-11-04T21:00:00Z"),
				null, DecidedBy.FORMULA, false, this.criteria, ASSIGNED, null), List.of());
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(43), this.other, Instant.parse("2026-11-01T00:00:00Z"),
				null, DecidedBy.FORMULA, false, this.criteria, ASSIGNED, null), List.of());

		assertThat(this.repository.publishedSpan(this.event)).contains(
				new PublishedSpan(Instant.parse("2026-11-04T21:00:00Z"), Instant.parse("2026-11-05T03:00:00Z")));
		assertThat(this.repository.publishedSpan(this.other + 1_000)).isEmpty();
	}

	@Test
	void deletesTheAssignmentsAndCandidatesOfTheGivenArticlesOnly() {
		assign(31, this.event, this.criteria, List.of(new StoredCandidate(this.other, 0.5, null)));
		assign(32, this.event, this.criteria, List.of(new StoredCandidate(this.other, 0.5, null)));

		this.repository.deleteByArticles(List.of(new ArticleId(31)));
		this.repository.deleteByArticles(List.of());

		assertThat(this.repository.exists(new ArticleId(31))).isFalse();
		assertThat(candidates(31)).isEmpty();
		assertThat(this.repository.exists(new ArticleId(32))).isTrue();
		assertThat(candidates(32)).hasSize(1);
		assertThat(this.repository.lockByArticles(List.of())).isEmpty();
	}

	@Test
	void aCandidateOfAMergedEventIsReadAsItsSurvivorAndFolded() {
		long merged = merged(this.event);
		assign(51, this.event, this.oldCriteria, List.of(new StoredCandidate(this.event, 0.60, 0.6),
				new StoredCandidate(merged, 0.62, 0.9), new StoredCandidate(this.other, 0.45, null)));

		List<RejudgeTarget> targets = this.repository.lockRejudgeTargets(this.criteria, 10);

		// 합쳐진 사건(0.62 · 0.9)을 남는 사건으로 읽어 지금 사건의 후보(0.60 · 0.6)와 하나로 모은다 — 점수 · 확률 모두 큰 값
		assertThat(targets).singleElement()
			.extracting(RejudgeTarget::stored)
			.isEqualTo(List.of(new StoredCandidate(this.event, 0.62, 0.9), new StoredCandidate(this.other, 0.45, null)));
	}

	@Test
	void priorProbabilitiesReadBothSidesFoldMergedEventsAndKeepTheHighest() {
		long third = event(1);
		long unrelated = event(1);
		long unjudged = event(1);
		long mergedIntoOther = merged(this.other);
		long mergedIntoEvent = merged(this.event);
		// 이 사건의 기사 — other 0.3, other 로 합쳐진 사건 0.5, third 확률 없음, 사라진 사건 0.9, 묻지 않은 사건 0.95,
		// 확률 없는 후보뿐인 사건
		assign(61, this.event, this.criteria, List.of(new StoredCandidate(this.other, 0.60, 0.3),
				new StoredCandidate(mergedIntoOther, 0.60, 0.5), new StoredCandidate(third, 0.60, null),
				new StoredCandidate(999_999, 0.60, 0.9), new StoredCandidate(unrelated, 0.60, 0.95),
				new StoredCandidate(unjudged, 0.60, null)));
		// 상대 사건의 기사 — other 의 기사가 이 사건 0.4, third 의 기사가 이 사건으로 합쳐진 사건 0.7
		assign(62, this.other, this.criteria, List.of(new StoredCandidate(this.event, 0.60, 0.4)));
		assign(63, third, this.criteria, List.of(new StoredCandidate(mergedIntoEvent, 0.60, 0.7)));

		assertThat(this.repository.priorProbabilities(this.event, List.of(this.other, third, unjudged, 999_999L)))
			.containsExactlyInAnyOrderEntriesOf(Map.of(this.other, 0.5, third, 0.7));
		assertThat(this.repository.priorProbabilities(this.event, List.of())).isEmpty();
	}

	@Test
	void keepClosedRewritesOnlyTheCriteriaOfAssignmentsInClosedEvents() {
		this.jdbc.sql("update events set status = 'CLOSED', closed_at = now() where id = :id")
			.param("id", this.other)
			.update();
		assign(61, this.other, this.oldCriteria, List.of());
		assign(62, this.other, this.oldCriteria, List.of());
		assign(63, this.other, this.criteria, List.of());
		assign(64, this.event, this.oldCriteria, List.of());

		int first = this.repository.keepClosed(this.criteria, 1);
		int rest = this.repository.keepClosed(this.criteria, 10);

		assertThat(first).isEqualTo(1);
		assertThat(rest).isEqualTo(1);
		assertThat(this.repository.lockByArticles(List.of(new ArticleId(61), new ArticleId(62), new ArticleId(64))))
			.extracting(EventArticle::criteriaId)
			.containsExactly(this.criteria, this.criteria, this.oldCriteria);
	}

	@Test
	void lockByEventsLocksTheAssignmentsOfTheEventsAndMoveAllKeepsTheRest() {
		assign(73, this.event, this.oldCriteria, List.of());
		assign(71, this.other, this.criteria, List.of());
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(72), this.event, PUBLISHED, null,
				DecidedBy.JUDGEMENT_FAILED, true, this.criteria, ASSIGNED, null), List.of());
		long third = event(1);
		assign(74, third, this.criteria, List.of());

		assertThat(this.repository.lockByEvents(List.of(this.event, this.other)))
			.extracting(EventArticle::articleId)
			.containsExactly(new ArticleId(71), new ArticleId(72), new ArticleId(73));
		assertThat(this.repository.lockByEvents(List.of())).isEmpty();

		this.repository.moveAll(this.event, this.other);

		// 사건만 바뀌고 연 사건 표지 · 점수 · 정한 것 · 기준 · 배정 시각은 그대로다
		assertThat(this.repository.lockByArticles(List.of(new ArticleId(72), new ArticleId(73), new ArticleId(74))))
			.containsExactly(
					new EventArticle(new ArticleId(72), this.other, PUBLISHED, null, DecidedBy.JUDGEMENT_FAILED, true,
							this.criteria, ASSIGNED, null),
					new EventArticle(new ArticleId(73), this.other, PUBLISHED, 0.5, DecidedBy.FORMULA, false,
							this.oldCriteria, ASSIGNED, null),
					new EventArticle(new ArticleId(74), third, PUBLISHED, 0.5, DecidedBy.FORMULA, false, this.criteria,
							ASSIGNED, null));
	}

	private void assign(long article, long event, long criteria, List<StoredCandidate> stored) {
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(article), event, PUBLISHED, 0.5, DecidedBy.FORMULA,
				false, criteria, ASSIGNED, null), stored);
	}

	private List<StoredCandidate> candidates(long article) {
		return this.jdbc.sql("""
				select event_id, score, probability from event_article_candidates where article_id = :article
				order by event_id
				""")
			.param("article", article)
			.query((rs, row) -> new StoredCandidate(rs.getLong("event_id"), rs.getDouble("score"),
					rs.getObject("probability", Double.class)))
			.list();
	}

	private long event(int articles) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', '[1,0]', :count, :t, :t, :t, :t) returning id
				""").param("count", articles).param("t", PUBLISHED.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	/** 기사 없이 {@code survivor} 에 합쳐진 사건 */
	private long merged(long survivor) {
		long merged = event(1);
		this.jdbc.sql("""
				update events set status = 'MERGED', merged_into = :survivor, closed_at = now() where id = :merged
				""").param("survivor", survivor).param("merged", merged).update();
		return merged;
	}

	private long criteria(double upper) {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, :upper, 0.55, 0.4, 0.5, now()) returning id
				""").param("upper", upper).query(Long.class).single();
	}

}
