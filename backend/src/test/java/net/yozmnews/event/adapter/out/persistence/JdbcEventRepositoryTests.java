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
import net.yozmnews.event.domain.event.MergeSubject;
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
		assertThat(opened.openedAt()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
		assertThat(opened.lastJoinedAt()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
		assertThat(revision(id)).isEqualTo(1);
		assertThat(termCounts(id)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 1, "한국은행", 1));
		assertThat(this.jdbc.sql("select created_at from events where id = :id")
			.param("id", id)
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
	}

	@Test
	void updateWritesTheEventAndCountsTermsByOccurrence() {
		long id = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		terms(id, Map.of("금리", 2, "동결", 1, "물가", 3));
		LockedEvent locked = this.repository.lock(id).orElseThrow();

		this.repository.update(new LockedEvent(id, locked.status(), locked.modelVersion(), new float[] { 0.5f, 0.5f }, 4,
				Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-05T12:00:00Z"), locked.openedAt(),
				Instant.parse("2026-11-06T07:00:00Z")),
				List.of("금리", "환율", "환율"), List.of("동결", "물가", "물가"));

		LockedEvent updated = this.repository.lock(id).orElseThrow();
		assertThat(updated.centroid()).containsExactly(0.5f, 0.5f);
		assertThat(updated.articleCount()).isEqualTo(4);
		assertThat(updated.firstArticleAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
		assertThat(updated.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z"));
		assertThat(updated.openedAt()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
		assertThat(updated.lastJoinedAt()).isEqualTo(Instant.parse("2026-11-06T07:00:00Z"));
		assertThat(revision(id)).isEqualTo(2);
		// 금리 2 + 1, 환율 새로 2(두 기사), 동결 1 − 1 → 지움, 물가 3 − 2
		assertThat(termCounts(id)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 3, "환율", 2, "물가", 1));
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
	void mergeDueAreOpenEventsChangedSinceTheirCheckOrCheckedByOtherCriteria() {
		long criteria = criteria(0.8);
		long other = criteria(0.9);
		long fresh = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T06:00:00Z");
		long checked = event("OPEN", "model-b", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long otherCriteria = event("OPEN", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		event("CLOSED", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		terms(fresh, Map.of("금리", 2, "동결", 1));
		this.repository.markMergeChecked(checked, 1, criteria);
		this.repository.markMergeChecked(otherCriteria, 1, other);
		// revision 이 다르면 표시하지 않는다 — 그새 바뀐 사건은 다시 본다
		this.repository.markMergeChecked(fresh, 2, criteria);

		List<MergeSubject> due = this.repository.mergeDue(criteria, 10);

		assertThat(due).extracting(MergeSubject::id).containsExactly(fresh, otherCriteria);
		MergeSubject subject = due.get(0);
		assertThat(subject.modelVersion()).isEqualTo("model-a");
		assertThat(subject.centroid()).containsExactly(1f, 0f);
		assertThat(subject.articleCount()).isEqualTo(1);
		assertThat(subject.keyTerms()).containsExactly("금리", "동결");
		assertThat(subject.firstArticleAt()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
		assertThat(subject.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T06:00:00Z"));
		assertThat(subject.revision()).isEqualTo(1);
		assertThat(this.repository.mergeDue(criteria, 1)).extracting(MergeSubject::id).containsExactly(fresh);
	}

	@Test
	void absorbWritesTheSurvivorAndLeavesTheAbsorbedPointingToIt() {
		long survivor = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long absorbed = event("OPEN", "model-a", "[0,1]", "2026-11-04T00:00:00Z", "2026-11-04T00:00:00Z");
		long earlier = event("OPEN", "model-a", "[0,1]", "2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z");
		terms(survivor, Map.of("금리", 2, "동결", 1));
		terms(absorbed, Map.of("금리", 1, "환율", 1));
		// 앞서 absorbed 에 합쳐진 사건 — 남는 사건으로 다시 가리켜야 한다
		this.jdbc.sql("update events set status = 'MERGED', merged_into = :into, closed_at = now() where id = :id")
			.param("into", absorbed)
			.param("id", earlier)
			.update();
		LockedEvent kept = this.repository.lock(survivor).orElseThrow();
		LockedEvent gone = this.repository.lock(absorbed).orElseThrow();
		Instant now = Instant.parse("2026-11-06T09:00:00Z");

		this.repository.absorb(kept.absorbed(gone), gone, now);

		LockedEvent written = this.repository.lock(survivor).orElseThrow();
		assertThat(written.articleCount()).isEqualTo(2);
		assertThat(written.centroid()).containsExactly(0.5f, 0.5f);
		assertThat(written.firstArticleAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
		assertThat(written.openedAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
		assertThat(revision(survivor)).isEqualTo(2);
		assertThat(termCounts(survivor)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 3, "동결", 1, "환율", 1));
		assertThat(termCounts(absorbed)).isEmpty();
		assertThat(this.jdbc.sql("""
				select status, merged_into, closed_at = :now as closed_now from events where id = :id
				""").param("id", absorbed).param("now", now.atOffset(ZoneOffset.UTC)).query().singleRow())
			.containsEntry("status", "MERGED")
			.containsEntry("merged_into", survivor)
			.containsEntry("closed_now", true);
		assertThat(this.jdbc.sql("select merged_into from events where id = :id")
			.param("id", earlier)
			.query(Long.class)
			.single()).isEqualTo(survivor);
	}

	@Test
	void deletingASurvivorTakesItsMergedRowsCandidatesAndJudgements() {
		long survivor = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long merged = event("OPEN", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long kept = event("OPEN", "model-a", "[0,1]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		this.jdbc.sql("update events set status = 'MERGED', merged_into = :into, closed_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
		candidate(80, merged);
		candidate(80, kept);
		this.jdbc.sql("""
				insert into event_merge_judgements (event_low, event_high, probability, judged_at)
				values (:a, :b, 0.4, now()), (:c, :d, 0.6, now())
				""").param("a", Math.min(merged, kept)).param("b", Math.max(merged, kept))
			.param("c", Math.min(survivor, kept)).param("d", Math.max(survivor, kept))
			.update();

		this.repository.delete(survivor);

		assertThat(this.jdbc.sql("select id from events").query(Long.class).list()).containsExactly(kept);
		assertThat(this.jdbc.sql("select event_id from event_article_candidates where article_id = 80")
			.query(Long.class)
			.list()).containsExactly(kept);
		assertThat(this.jdbc.sql("select count(*) from event_merge_judgements").query(Long.class).single()).isZero();
	}

	@Test
	void closableAreOpenEventsIdleOrOldEnoughFromTheCursorAndCloseWritesTheRepresentative() {
		long idle = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long fresh = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long old = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long closed = event("CLOSED", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		times(idle, "2026-11-10T00:00:00Z", "2026-11-13T00:00:00Z");
		times(fresh, "2026-11-10T00:00:00Z", "2026-11-13T00:00:01Z");
		times(old, "2026-11-06T00:00:00Z", "2026-11-19T00:00:00Z");
		times(closed, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
		Instant idleBefore = Instant.parse("2026-11-13T00:00:00Z");
		Instant openedBefore = Instant.parse("2026-11-06T00:00:00Z");

		// 경계는 포함한다 — 마지막으로 붙은 시각 = idleBefore, 연 시각 = openedBefore
		assertThat(this.repository.closable(idleBefore, openedBefore, 0, 10)).containsExactly(idle, old);
		assertThat(this.repository.closable(idleBefore, openedBefore, 0, 1)).containsExactly(idle);
		assertThat(this.repository.closable(idleBefore, openedBefore, idle, 10)).containsExactly(old);

		this.repository.close(idle, new ArticleId(55), Instant.parse("2026-11-20T00:00:00Z"));
		this.repository.close(old, null, Instant.parse("2026-11-20T00:00:00Z"));
		this.repository.clearRepresentative(List.of(new ArticleId(56)));

		assertThat(this.jdbc.sql("""
				select status, representative_article_id, closed_at = '2026-11-20T00:00:00Z' as closed_now
				from events where id = :id
				""").param("id", idle).query().singleRow()).containsEntry("status", "CLOSED")
			.containsEntry("representative_article_id", 55L)
			.containsEntry("closed_now", true);
		assertThat(this.repository.lock(old).orElseThrow().status()).isEqualTo(EventStatus.CLOSED);

		this.repository.clearRepresentative(List.of(new ArticleId(55)));

		assertThat(this.jdbc.sql("select representative_article_id from events where id = :id")
			.param("id", idle)
			.query()
			.singleRow()).containsEntry("representative_article_id", null);
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
				                    created_at, last_joined_at, closed_at)
				values (:status, :model, cast(:centroid as halfvec), 1, :first, :last, :first, :first,
				        case when :status = 'OPEN' then null else cast(:first as timestamptz) end)
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

	private void times(long event, String opened, String joined) {
		this.jdbc.sql("update events set created_at = :opened, last_joined_at = :joined where id = :id")
			.param("opened", Instant.parse(opened).atOffset(ZoneOffset.UTC))
			.param("joined", Instant.parse(joined).atOffset(ZoneOffset.UTC))
			.param("id", event)
			.update();
	}

	private long revision(long event) {
		return this.jdbc.sql("select revision from events where id = :id").param("id", event).query(Long.class).single();
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
		return criteria(0.8);
	}

	private long criteria(double upper) {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, :upper, 0.55, 0.4, 0.5, now()) returning id
				""").param("upper", upper).query(Long.class).single();
	}

	private void member(long article, long event, long criteria, String model, String embedding) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, decided_by, opened, criteria_id, assigned_at)
				values (:article, :event, now(), 'FORMULA', false, :criteria, now())
				""").param("article", article).param("event", event).param("criteria", criteria).update();
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, :model, cast(:embedding as vector), '{}', now())
				""").param("article", article).param("model", model).param("embedding", embedding).update();
	}

}
