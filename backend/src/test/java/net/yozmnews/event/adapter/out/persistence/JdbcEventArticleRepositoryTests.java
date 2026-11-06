package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.DecidedBy;
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
				this.criteria, ASSIGNED);

		boolean inserted = this.repository.insertIfAbsent(row, List.of(new StoredCandidate(this.event, 0.72, 0.6),
				new StoredCandidate(this.other, 0.45, null)));
		boolean again = this.repository.insertIfAbsent(new EventArticle(new ArticleId(1), this.other, PUBLISHED, null,
				DecidedBy.FORMULA, this.criteria, ASSIGNED), List.of(new StoredCandidate(this.other, 0.9, null)));

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
				this.criteria, ASSIGNED);
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

		this.repository.reassign(new ArticleId(21), this.other, null, DecidedBy.EVALUATOR, this.criteria,
				Instant.parse("2026-11-06T00:00:00Z"));
		this.repository.keep(new ArticleId(22), this.criteria);

		assertThat(this.repository.lockByArticles(List.of(new ArticleId(22), new ArticleId(21)))).containsExactly(
				new EventArticle(new ArticleId(21), this.other, PUBLISHED, null, DecidedBy.EVALUATOR, this.criteria,
						Instant.parse("2026-11-06T00:00:00Z")),
				new EventArticle(new ArticleId(22), this.event, PUBLISHED, 0.5, DecidedBy.FORMULA, this.criteria,
						ASSIGNED));
	}

	@Test
	void thePublishedSpanCoversTheRemainingAssignmentsOfTheEvent() {
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(41), this.event, Instant.parse("2026-11-05T03:00:00Z"),
				null, DecidedBy.FORMULA, this.criteria, ASSIGNED), List.of());
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(42), this.event, Instant.parse("2026-11-04T21:00:00Z"),
				null, DecidedBy.FORMULA, this.criteria, ASSIGNED), List.of());
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(43), this.other, Instant.parse("2026-11-01T00:00:00Z"),
				null, DecidedBy.FORMULA, this.criteria, ASSIGNED), List.of());

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

	private void assign(long article, long event, long criteria, List<StoredCandidate> stored) {
		this.repository.insertIfAbsent(new EventArticle(new ArticleId(article), event, PUBLISHED, 0.5, DecidedBy.FORMULA,
				criteria, ASSIGNED), stored);
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
				                    created_at)
				values ('OPEN', 'model-a', '[1,0]', :count, :t, :t, :t) returning id
				""").param("count", articles).param("t", PUBLISHED.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	private long criteria(double upper) {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, :upper, 0.55, 0.4, 0.5, now()) returning id
				""").param("upper", upper).query(Long.class).single();
	}

}
