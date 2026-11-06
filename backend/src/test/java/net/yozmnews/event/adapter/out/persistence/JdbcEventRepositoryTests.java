package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.EventSnapshot;
import net.yozmnews.event.domain.event.EventStatus;
import net.yozmnews.event.domain.event.LockedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 사건 저장소 — 후보 사건의 조건(열림 · 모델 버전 · 시각 범위)과 순서, 사건 핵심 말 상위 수와 순서, 정확한 코사인, 반 정밀도 중심의
 * 왕복, 서술 기사 순서, 잠금 · 열기 · 고치기 · 지우기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: event-R10 · event-R11 · event-R12
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEventRepository.class })
class JdbcEventRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	@Autowired
	JdbcEventRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void candidatesAreOpenEventsOfTheModelOverlappingTheRangeInIdOrder() {
		long near = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T06:00:00Z");
		long far = event("OPEN", "model-a", "[0,1]", "2026-11-03T00:00:00Z", "2026-11-04T00:00:00Z");
		event("CLOSED", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T06:00:00Z");
		event("OPEN", "model-b", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T06:00:00Z");
		// 범위 앞에서 끝난 사건 · 범위 뒤에서 시작한 사건
		event("OPEN", "model-a", "[1,0]", "2026-11-01T00:00:00Z", "2026-11-03T23:59:59Z");
		event("OPEN", "model-a", "[1,0]", "2026-11-06T00:00:01Z", "2026-11-07T00:00:00Z");
		// 범위 끝에서 시작한 사건 — 경계는 포함
		long edge = event("OPEN", "model-a", "[1,0]", "2026-11-06T00:00:00Z", "2026-11-07T00:00:00Z");
		terms(near, Map.of("국회", 3, "예산", 2, "여당", 2, "야당", 1));

		List<EventSnapshot> candidates = this.repository.candidates("model-a", new float[] { 3f, 4f },
				Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-06T00:00:00Z"));

		assertThat(candidates).extracting(EventSnapshot::eventId).containsExactly(near, far, edge);
		// 코사인 — [3, 4] 와 [1, 0] 은 3/5 = 0.6, [0, 1] 과는 4/5 = 0.8. 기사 벡터도 반 정밀도로 바뀌므로 반 정밀도로 정확한 값을 쓴다
		assertThat(candidates.get(0).cosine()).isCloseTo(0.6, within(1e-6));
		assertThat(candidates.get(1).cosine()).isCloseTo(0.8, within(1e-6));
		// 기사 수가 많은 순, 같으면 말의 바이트 순(여 U+C5EC < 예 U+C608)
		assertThat(candidates.get(0).keyTerms()).containsExactly("국회", "여당", "예산", "야당");
		assertThat(candidates.get(0).firstArticleAt()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
		assertThat(candidates.get(0).lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T06:00:00Z"));
		assertThat(candidates.get(1).keyTerms()).isEmpty();
		assertThat(this.repository.candidates("model-a", new float[] { 3f, 4f },
				Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-06T00:00:00Z")))
			.isEqualTo(candidates);
	}

	@Test
	void aZeroCentroidHasNoCosine() {
		event("OPEN", "model-a", "[0,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");

		List<EventSnapshot> candidates = this.repository.candidates("model-a", new float[] { 3f, 4f },
				Instant.parse("2026-11-05T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"));

		// pgvector 는 영 벡터와의 코사인 거리를 NaN 으로 낸다 — 점수도 NaN 이라 저장 기준 비교에서 빠진다
		assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.cosine()).isNaN());
	}

	@Test
	void candidatesCarryTheTopTwentyKeyTerms() {
		long id = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		Map<String, Integer> many = new HashMap<>();
		for (int i = 0; i < 21; i++) {
			many.put("말%02d".formatted(i), 1);
		}
		terms(id, many);

		List<String> keyTerms = this.repository
			.candidates("model-a", new float[] { 1, 0 }, Instant.parse("2026-11-05T00:00:00Z"),
					Instant.parse("2026-11-05T00:00:00Z"))
			.get(0)
			.keyTerms();

		assertThat(keyTerms).hasSize(20).startsWith("말00").endsWith("말19").doesNotContain("말20");
	}

	@Test
	void openWritesOneArticleEventWithDistinctTermsAndHalfPrecisionCentroid() {
		long id = this.repository.open("model-a", new float[] { 0.5f, -1f, 0.1f }, T, List.of("금리", "한국은행", "금리"),
				Instant.parse("2026-11-06T00:00:00Z"));

		LockedEvent opened = this.repository.lock(id).orElseThrow();
		assertThat(opened.status()).isEqualTo(EventStatus.OPEN);
		assertThat(opened.modelVersion()).isEqualTo("model-a");
		// 0.1 의 반 정밀도 값은 0.0999755859375 다
		assertThat(opened.centroid()).containsExactly(0.5f, -1f, 0.0999755859375f);
		assertThat(opened.articleCount()).isEqualTo(1);
		assertThat(opened.firstArticleAt()).isEqualTo(T);
		assertThat(opened.lastArticleAt()).isEqualTo(T);
		assertThat(termCounts(id)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 1, "한국은행", 1));
		assertThat(this.jdbc.sql("select created_at from events where id = :id")
			.param("id", id)
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
	}

	@Test
	void updateWritesTheEventAndCountsTermsOnce() {
		long id = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		terms(id, Map.of("금리", 2, "동결", 1, "물가", 3));
		LockedEvent locked = this.repository.lock(id).orElseThrow();

		this.repository.update(new LockedEvent(id, locked.status(), locked.modelVersion(), new float[] { 0.5f, 0.5f }, 4,
				Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-05T12:00:00Z")),
				List.of("금리", "환율", "환율"), List.of("동결", "물가", "물가"));

		LockedEvent updated = this.repository.lock(id).orElseThrow();
		assertThat(updated.centroid()).containsExactly(0.5f, 0.5f);
		assertThat(updated.articleCount()).isEqualTo(4);
		assertThat(updated.firstArticleAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
		assertThat(updated.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
		assertThat(termCounts(id)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 3, "환율", 1, "물가", 2));
	}

	@Test
	void lockAllReturnsExistingEventsInIdOrder() {
		long first = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long second = event("CLOSED", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");

		assertThat(this.repository.lockAll(List.of(second, first, second + 1_000)))
			.extracting(LockedEvent::id, LockedEvent::status)
			.containsExactly(tuple(first, EventStatus.OPEN),
					tuple(second, EventStatus.CLOSED));
		assertThat(this.repository.lockAll(List.of())).isEmpty();
		assertThat(this.repository.lock(second + 1_000)).isEmpty();
	}

	@Test
	void deleteRemovesTheEventItsTermsAndTheCandidatesPointingToIt() {
		long gone = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long kept = event("OPEN", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		terms(gone, Map.of("금리", 1));
		candidate(70, gone);
		candidate(70, kept);
		candidate(71, gone);

		this.repository.delete(gone);

		assertThat(this.jdbc.sql("select id from events where id in (:ids)").param("ids", List.of(gone, kept))
			.query(Long.class)
			.list()).containsExactly(kept);
		assertThat(termCounts(gone)).isEmpty();
		assertThat(this.jdbc.sql("select event_id from event_article_candidates where article_id in (70, 71)")
			.query(Long.class)
			.list()).containsExactly(kept);
	}

	@Test
	void describersAreMembersWithTheModelClosestToTheCentroid() {
		long id = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long other = event("OPEN", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long criteria = criteria();
		member(81, id, criteria, "model-a", "[0,1]");
		member(82, id, criteria, "model-a", "[1,0]");
		member(83, id, criteria, "model-a", "[0.6,0.8]");
		// 같은 거리 — 기사 ID 순
		member(84, id, criteria, "model-a", "[1,0]");
		// 다른 모델 버전의 표현만 있는 기사 · 다른 사건의 기사
		member(85, id, criteria, "model-b", "[1,0]");
		member(86, other, criteria, "model-a", "[1,0]");

		assertThat(this.repository.describers(id, "model-a", 3)).containsExactly(new ArticleId(82), new ArticleId(84),
				new ArticleId(83));
		assertThat(this.repository.describers(id, "model-a", 10)).containsExactly(new ArticleId(82), new ArticleId(84),
				new ArticleId(83), new ArticleId(81));
	}

	private long event(String status, String model, String centroid, String first, String last) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at)
				values (:status, :model, cast(:centroid as halfvec), 1, :first, :last, :first)
				returning id
				""")
			.param("status", status)
			.param("model", model)
			.param("centroid", centroid)
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
	}

	private void terms(long event, Map<String, Integer> counts) {
		counts.forEach((term, count) -> this.jdbc
			.sql("insert into event_key_terms (event_id, term, article_count) values (:event, :term, :count)")
			.param("event", event)
			.param("term", term)
			.param("count", count)
			.update());
	}

	private Map<String, Integer> termCounts(long event) {
		Map<String, Integer> counts = new HashMap<>();
		this.jdbc.sql("select term, article_count from event_key_terms where event_id = :event")
			.param("event", event)
			.query((ResultSet rs) -> {
				counts.put(rs.getString("term"), rs.getInt("article_count"));
			});
		return counts;
	}

	private void candidate(long article, long event) {
		this.jdbc.sql("insert into event_article_candidates (article_id, event_id, score) values (:article, :event, 0.5)")
			.param("article", article)
			.param("event", event)
			.update();
	}

	private long criteria() {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
	}

	private void member(long article, long event, long criteria, String model, String embedding) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, decided_by, criteria_id, assigned_at)
				values (:article, :event, now(), 'FORMULA', :criteria, now())
				""").param("article", article).param("event", event).param("criteria", criteria).update();
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, :model, cast(:embedding as vector), '{}', now())
				""").param("article", article).param("model", model).param("embedding", embedding).update();
	}

}
