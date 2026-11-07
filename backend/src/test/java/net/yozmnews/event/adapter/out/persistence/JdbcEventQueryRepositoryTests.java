package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 사건 조회 저장소 — 대표 기사의 정렬(표현이 있는 기사의 거리 순 → 표현 없는 기사의 발행 시각 순, 사건 모델 버전의 표현만), 사건 · 배정
 * 읽기, 기사의 사건, 기간 안 배정, 사건 쪽 넘기기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 시험은 시험 트랜잭션 안에서 돌고
 * 되돌린다 — 기간 조회는 다른 시험의 행과 겹치지 않게 먼 시각(2031년)을 쓴다.
 * <p>
 * 지키는 규칙: event-R14 · event-R20
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEventQueryRepository.class })
class JdbcEventQueryRepositoryTests {

	@Autowired
	JdbcEventQueryRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theRepresentativeIsTheClosestMemberWithAVectorOfTheEventModelThenTheEarliestWithout() {
		long event = event("OPEN", "model-a", "[1,0]", "2026-11-02T00:00:00Z", "2026-11-05T00:00:00Z");
		long other = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long criteria = criteria();
		member(81, event, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(81, "model-a", "[0,1]");
		member(82, event, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(82, "model-a", "[1,0]");
		member(83, event, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(83, "model-a", "[0.6,0.8]");
		// 84 · 85 — 표현 없음. 87 — 사건과 다른 모델 버전의 표현만(거리 0 이지만 표현 없음으로 친다)
		member(84, event, criteria, "2026-11-04T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(85, event, criteria, "2026-11-03T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(87, event, criteria, "2026-11-02T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(87, "model-b", "[1,0]");
		// 86 — 다른 사건의 기사
		member(86, other, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(86, "model-a", "[1,0]");

		assertThat(this.repository.representativesOfAll(List.of(event))).isEqualTo(Map.of(event, new ArticleId(82)));
		assertThat(this.repository.representatives(Map.of(event, ids(81, 84, 85, 86))))
			.isEqualTo(Map.of(event, new ArticleId(81)));
		assertThat(this.repository.representatives(Map.of(event, ids(84, 85)))).isEqualTo(Map.of(event, new ArticleId(85)));
		assertThat(this.repository.representatives(Map.of(event, ids(81, 87)))).isEqualTo(Map.of(event, new ArticleId(81)));
		assertThat(this.repository.representatives(Map.of(event, ids(85, 87)))).isEqualTo(Map.of(event, new ArticleId(87)));
		assertThat(this.repository.representatives(Map.of(event, ids(86)))).isEmpty();
		assertThat(this.repository.representatives(Map.of())).isEmpty();
	}

	@Test
	void aMergedEventIsFoundAsItsSurvivorAndIsNotPaged() {
		long survivor = event("OPEN", "model-a", "[1,0]", "2026-11-03T00:00:00Z", "2026-11-05T00:00:00Z");
		long merged = event("OPEN", "model-a", "[0,1]", "2026-11-04T00:00:00Z", "2026-11-04T00:00:00Z");
		long criteria = criteria();
		member(95, survivor, criteria, "2026-11-04T00:00:00Z", DecidedBy.FORMULA, 0.9);
		this.jdbc.sql("""
				update events set status = 'MERGED', merged_into = :survivor, closed_at = now() where id = :merged
				""").param("survivor", survivor).param("merged", merged).update();

		// 합쳐진 사건의 ID 로 찾으면 남는 사건을 낸다
		assertThat(this.repository.find(merged)).map(view -> view.id().value()).contains(survivor);
		assertThat(this.repository.find(merged)).isEqualTo(this.repository.find(survivor));
		// 합쳐진 사건은 배정이 없어 목록에 나오지 않는다
		assertThat(this.repository.page(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"), null,
				null, 10))
			.extracting(view -> view.id().value())
			.containsExactly(survivor);
	}

	@Test
	void theRepresentativeLeftAtClosingComesFirst() {
		long closed = event("CLOSED", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long open = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long criteria = criteria();
		// 닫힌 사건 — 표현이 지워져 정렬로는 발행 시각이 이른 96 이 나오지만, 닫을 때 남긴 97 이 대표다
		member(96, closed, criteria, "2026-11-04T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(97, closed, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		this.jdbc.sql("update events set representative_article_id = 97 where id = :id").param("id", closed).update();
		member(98, open, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		vector(98, "model-a", "[1,0]");

		assertThat(this.repository.representativesOfAll(List.of(closed, open)))
			.isEqualTo(Map.of(closed, new ArticleId(97), open, new ArticleId(98)));
		// 걸러진 기사에서 고를 때는 저장값을 쓰지 않는다
		assertThat(this.repository.representatives(Map.of(closed, ids(96, 97)))).isEqualTo(Map.of(closed, new ArticleId(96)));
	}

	@Test
	void readsTheEventAndItsAssignmentsInPublicationOrder() {
		long event = event("CLOSED", "model-a", "[1,0]", "2026-11-03T00:00:00Z", "2026-11-05T00:00:00Z");
		long criteria = criteria();
		member(92, event, criteria, "2026-11-05T00:00:00Z", DecidedBy.EVALUATOR, 0.7);
		member(91, event, criteria, "2026-11-05T00:00:00Z", DecidedBy.JUDGEMENT_FAILED, null);
		member(93, event, criteria, "2026-11-03T00:00:00Z", DecidedBy.FORMULA, 0.85);

		assertThat(this.repository.find(event)).contains(new EventView(new EventId(event), EventStatus.CLOSED, 3,
				Instant.parse("2026-11-03T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"),
				Instant.parse("2026-11-03T00:00:00Z")));
		assertThat(this.repository.find(event + 1_000)).isEmpty();
		Instant assigned = Instant.parse("2026-11-06T00:00:00Z");
		assertThat(this.repository.articles(event)).containsExactly(
				new EventArticleView(new ArticleId(93), Instant.parse("2026-11-03T00:00:00Z"), 0.85, DecidedBy.FORMULA,
						assigned),
				new EventArticleView(new ArticleId(91), Instant.parse("2026-11-05T00:00:00Z"), null,
						DecidedBy.JUDGEMENT_FAILED, assigned),
				new EventArticleView(new ArticleId(92), Instant.parse("2026-11-05T00:00:00Z"), 0.7, DecidedBy.EVALUATOR,
						assigned));
		assertThat(this.repository.articles(event + 1_000)).isEmpty();
	}

	@Test
	void findsTheEventsOfArticlesWithAnAssignmentOnly() {
		long first = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long second = event("OPEN", "model-a", "[1,0]", "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		long criteria = criteria();
		member(101, first, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(102, second, criteria, "2026-11-05T00:00:00Z", DecidedBy.FORMULA, 0.9);

		assertThat(this.repository.eventsOf(ids(102, 101, 103)))
			.isEqualTo(Map.of(new ArticleId(101), first, new ArticleId(102), second));
		assertThat(this.repository.eventsOf(List.of())).isEmpty();
	}

	@Test
	void membersInAPeriodIncludeTheStartAndExcludeTheEnd() {
		Instant from = Instant.parse("2031-01-01T00:00:00Z");
		Instant to = Instant.parse("2031-01-02T00:00:00Z");
		long first = event("OPEN", "model-a", "[1,0]", "2030-12-31T23:59:59Z", "2031-01-02T00:00:00Z");
		// 둘째 사건은 닫혔고 모델 버전이 다르다 — 기간 조회는 사건의 상태 · 모델 버전을 가리지 않는다
		long second = event("CLOSED", "model-b", "[1,0]", "2031-01-01T12:00:00Z", "2031-01-01T12:00:00Z");
		long criteria = criteria();
		member(111, first, criteria, "2030-12-31T23:59:59Z", DecidedBy.FORMULA, 0.9);
		member(113, first, criteria, "2031-01-01T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(112, first, criteria, "2031-01-01T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(114, first, criteria, "2031-01-02T00:00:00Z", DecidedBy.FORMULA, 0.9);
		member(115, second, criteria, "2031-01-01T12:00:00Z", DecidedBy.FORMULA, 0.9);

		Map<Long, List<ArticleId>> expected = new LinkedHashMap<>();
		expected.put(first, ids(112, 113));
		expected.put(second, ids(115));
		assertThat(this.repository.membersIn(from, to)).containsExactlyEntriesOf(expected);
		assertThat(this.repository.membersIn(List.of(second), from, to)).containsExactlyEntriesOf(Map.of(second, ids(115)));
		assertThat(this.repository.membersIn(List.of(), from, to)).isEmpty();
	}

	@Test
	void pagesEventsWithAMemberInThePeriodByTheLastArticleTimeThenId() {
		Instant from = Instant.parse("2031-02-01T00:00:00Z");
		Instant to = Instant.parse("2031-02-02T00:00:00Z");
		long criteria = criteria();
		// early 는 닫혔고 모델 버전이 다르다 — 목록은 사건의 상태 · 모델 버전을 가리지 않는다
		long early = event("CLOSED", "model-b", "[1,0]", "2031-02-01T01:00:00Z", "2031-02-01T01:00:00Z");
		long lateA = event("OPEN", "model-a", "[1,0]", "2031-02-01T02:00:00Z", "2031-02-03T00:00:00Z");
		long lateB = event("OPEN", "model-a", "[1,0]", "2031-02-01T03:00:00Z", "2031-02-03T00:00:00Z");
		long outside = event("OPEN", "model-a", "[1,0]", "2031-02-02T00:00:00Z", "2031-02-02T00:00:00Z");
		member(121, early, criteria, "2031-02-01T01:00:00Z", DecidedBy.FORMULA, 0.9);
		member(122, lateA, criteria, "2031-02-01T02:00:00Z", DecidedBy.FORMULA, 0.9);
		member(123, lateB, criteria, "2031-02-01T03:00:00Z", DecidedBy.FORMULA, 0.9);
		member(124, outside, criteria, "2031-02-02T00:00:00Z", DecidedBy.FORMULA, 0.9);

		List<EventView> first = this.repository.page(from, to, null, null, 2);
		assertThat(first).extracting(view -> view.id().value()).containsExactly(lateB, lateA);
		EventView last = first.getLast();
		assertThat(this.repository.page(from, to, last.lastArticleAt(), last.id().value(), 2))
			.extracting(view -> view.id().value())
			.containsExactly(early);
	}

	private static List<ArticleId> ids(long... values) {
		return Arrays.stream(values).mapToObj(ArticleId::new).toList();
	}

	private long event(String status, String model, String centroid, String first, String last) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, closed_at)
				values (:status, :model, cast(:centroid as halfvec), 3, :first, :last, :first, :first,
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

	private long criteria() {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now())
				on conflict (window_seconds, upper, lower, store, probability) do update set created_at = excluded.created_at
				returning id
				""").query(Long.class).single();
	}

	private void member(long article, long event, long criteria, String published, DecidedBy by, @Nullable Double score) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, :score, :by, false, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("article", article)
			.param("event", event)
			.param("published", Instant.parse(published).atOffset(ZoneOffset.UTC))
			.param("score", score, Types.DOUBLE)
			.param("by", by.name())
			.param("criteria", criteria)
			.update();
	}

	private void vector(long article, String model, String embedding) {
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, :model, cast(:embedding as vector), '{}', now())
				""").param("article", article).param("model", model).param("embedding", embedding).update();
	}

}
