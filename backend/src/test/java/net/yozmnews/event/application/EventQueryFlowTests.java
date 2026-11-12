package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventSummary;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 사건 조회와 기간 집계의 흐름 — 기사 · 엔트리 · 평가 · 사건 · 배정 행을 SQL 로 넣고, 공개 조회가 다른 모듈의 공개 조회로 소스 · 중요도를
 * 읽어 기간 안에서 세는지, 사건 목록의 쪽 넘기기, 사건 묶음의 기간 집계(같은 사건 한 번 · 기간 밖 기사 제외)와 기간 안 사건 · 사건 묶음
 * 조회를 실제 PostgreSQL 에서 본다.
 * <p>
 * 사건 · 기사 행과 이 시험이 넣은 평가 행은 시작과 끝에 지운다.
 * <p>
 * 지키는 규칙: event-R14 · event-R15 · event-R20
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventQueryFlowTests {

	/** 기간 [11-05T00, 11-06T00) */
	private static final EventPeriod PERIOD = new EventPeriod(Instant.parse("2026-11-05T00:00:00Z"),
			Instant.parse("2026-11-06T00:00:00Z"));

	@Autowired
	EventDirectory directory;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> evaluated = new ArrayList<>();

	private long x1;

	private long x2;

	private long x3;

	private long x4;

	private long y1;

	private long eventX;

	private long eventY;

	@BeforeEach
	void startFromTheFixture() {
		clear();
		long criteriaId = assignmentCriteria();
		// 사건 Y 를 먼저 연다 — 사건 ID 순과 마지막 기사 시각 순이 어긋난다
		this.eventY = event("2026-11-05T15:00:00Z", "2026-11-05T15:00:00Z");
		// 사건 X — 기간 안 기사 셋(소스 1 · 2 · 3, 중요도 3.0 · 4.0 · 없음), 끝 시각에 발행한 기간 밖 기사 하나(소스 4, 중요도 5.0)
		this.eventX = event("2026-11-05T00:00:00Z", "2026-11-06T00:00:00Z");
		this.x1 = article(1, 3.0);
		this.x2 = article(2, 4.0);
		this.x3 = article(3, null);
		this.x4 = article(4, 5.0);
		assign(this.x1, this.eventX, criteriaId, "2026-11-05T00:00:00Z");
		assign(this.x2, this.eventX, criteriaId, "2026-11-05T10:00:00Z");
		assign(this.x3, this.eventX, criteriaId, "2026-11-05T20:00:00Z");
		assign(this.x4, this.eventX, criteriaId, "2026-11-06T00:00:00Z");
		// 사건 Y — 소스 둘(1 · 2), 중요도 5.0 · 5.0
		this.y1 = article(1, 5.0);
		assign(this.y1, this.eventY, criteriaId, "2026-11-05T15:00:00Z");
		assign(article(2, 5.0), this.eventY, criteriaId, "2026-11-05T15:00:00Z");
	}

	@AfterEach
	void endWithoutTheFixture() {
		clear();
	}

	@Test
	void aGroupOfEventsIsTalliedOnceOverItsArticlesInThePeriod() {
		EventId x = new EventId(this.eventX);
		EventId y = new EventId(this.eventY);
		Map<String, List<EventId>> groups = new LinkedHashMap<>();
		// X(소스 1 · 2 · 3, 중요도 3.0 · 4.0 · 없음)와 Y(소스 1 · 2, 중요도 5.0 · 5.0) — 사건별 소스 수를 더하면 5 다
		groups.put("both", List.of(x, y));
		// 같은 사건을 두 번 넘겨도 한 번만 센다
		groups.put("twice", List.of(x, x));
		// 한 사건이 여러 묶음에 들면 묶음마다 센다
		groups.put("y", List.of(y));
		// 없는 사건뿐인 묶음
		groups.put("none", List.of(new EventId(999_999_999L)));

		Map<String, EventTally> tallies = this.directory.tallies(groups, PERIOD);

		assertThat(tallies.keySet()).containsExactly("both", "twice", "y", "none");
		// 기간 밖 기사(X 의 넷째 — 소스 4, 중요도 5.0)는 들지 않는다
		assertThat(tallies.get("both").articles()).isEqualTo(5);
		assertThat(tallies.get("both").sources()).isEqualTo(3);
		assertThat(tallies.get("both").importance()).isEqualByComparingTo("4.25");
		assertThat(tallies.get("twice")).isEqualTo(new EventTally(3, 3, new BigDecimal("3.5")));
		assertThat(tallies.get("y").articles()).isEqualTo(2);
		assertThat(tallies.get("y").sources()).isEqualTo(2);
		assertThat(tallies.get("y").importance()).isEqualByComparingTo("5");
		assertThat(tallies.get("none")).isEqualTo(new EventTally(0, 0, null));
		assertThat(this.directory.tallies(Map.<String, List<EventId>>of(), PERIOD)).isEmpty();
	}

	@Test
	void eventsWithArticlesInThePeriodAreListedWithTheirCountsAndViews() {
		EventId x = new EventId(this.eventX);
		EventId y = new EventId(this.eventY);

		// 사건 ID 순 — Y 를 먼저 열었다. X 의 기간 밖 기사는 세지 않는다
		Map<EventId, Integer> expected = new LinkedHashMap<>();
		expected.put(y, 2);
		expected.put(x, 3);
		assertThat(this.directory.articleCountsIn(PERIOD)).containsExactlyEntriesOf(expected);

		Map<EventId, EventView> views = this.directory.views(List.of(x, y, new EventId(999_999_999L)));
		assertThat(views.keySet()).containsExactly(y, x);
		assertThat(views.get(x)).isEqualTo(this.directory.find(x).orElseThrow());
		assertThat(this.directory.views(List.of())).isEmpty();
	}

	@Test
	void theListPagesEventsWithTheirPeriodTallyAndRepresentative() {
		EventPage first = this.directory.list(PERIOD, null, 1);

		// X 의 마지막 기사 시각(11-06T00)이 Y(11-05T15)보다 늦다. 대표 기사는 사건 기사 전체에서 — 표현이 없어 가장 먼저 발행한 x1
		assertThat(first.events()).hasSize(1);
		EventSummary x = first.events().getFirst();
		assertThat(x.event().id()).isEqualTo(new EventId(this.eventX));
		assertThat(x.tally()).isEqualTo(new EventTally(3, 3, new BigDecimal("3.5")));
		assertThat(x.representative()).isEqualTo(new ArticleId(this.x1));
		assertThat(first.next()).isNotNull();

		EventPage second = this.directory.list(PERIOD, first.next(), 1);
		assertThat(second.events()).extracting(summary -> summary.event().id()).containsExactly(new EventId(this.eventY));
		assertThat(second.events().getFirst().tally()).isEqualTo(new EventTally(2, 2, new BigDecimal("5.0")));
		assertThat(second.next()).isNull();
	}

	@Test
	void thePageSizeIsOneToTheLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.list(PERIOD, null, 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.list(PERIOD, null, EventDirectory.MAX_PAGE_SIZE + 1));
		assertThat(this.directory.list(PERIOD, null, EventDirectory.MAX_PAGE_SIZE).events()).hasSize(2);
	}

	@Test
	void theLinkingMaterialQueriesCheckTheirRanges() {
		EventId x = new EventId(this.eventX);
		Instant at = Instant.parse("2026-11-05T00:00:00Z");
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.profiles(List.of(x), 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.profiles(List.of(x), EventDirectory.MAX_KEY_TERMS + 1));
		assertThat(this.directory.profiles(List.of(x), EventDirectory.MAX_KEY_TERMS)).containsOnlyKeys(x);
		assertThat(this.directory.profiles(List.of(), 1)).isEmpty();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.neighbors(x, at.plusSeconds(1), at, -1, 20, 10));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.neighbors(x, at, at, -1, 0, 10));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.neighbors(x, at, at, -1, 20, 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.neighbors(x, at, at, -1, 20, EventDirectory.MAX_NEIGHBORS + 1));
		assertThat(this.directory.neighbors(x, at, at, -1, EventDirectory.MAX_KEY_TERMS, EventDirectory.MAX_NEIGHBORS))
			.isNotNull();
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.termCounts(List.of("말"), at.plusSeconds(1), at));
		List<String> terms = new ArrayList<>();
		for (int i = 0; i < EventDirectory.MAX_TERMS; i++) {
			terms.add("말" + i);
		}
		// 겹친 말은 한 번만 센다 — 서로 다른 말이 상한이면 받는다
		terms.add("말0");
		assertThat(this.directory.termCounts(terms, at, at).eventsByTerm()).hasSize(EventDirectory.MAX_TERMS);
		terms.add("말" + EventDirectory.MAX_TERMS);
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.termCounts(terms, at, at));
		assertThat(this.directory.termCounts(List.of(), at, at).eventsByTerm()).isEmpty();
	}

	@Test
	void eventsOfArticlesAndRepresentativesAmongFilteredArticles() {
		assertThat(this.directory.eventsOf(List.of(new ArticleId(this.x2), new ArticleId(this.x4))))
			.containsEntry(new ArticleId(this.x2), new EventId(this.eventX))
			.containsEntry(new ArticleId(this.x4), new EventId(this.eventX))
			.hasSize(2);
		assertThat(this.directory.representatives(
				Map.of(new EventId(this.eventX), List.of(new ArticleId(this.x3), new ArticleId(this.x2)))))
			.containsExactly(Map.entry(new EventId(this.eventX), new ArticleId(this.x2)));
		assertThat(this.directory.find(new EventId(this.eventY))).isPresent();
		assertThat(this.directory.articles(new EventId(this.eventX))).hasSize(4);
	}

	@Test
	void theWholeEventRepresentativeIsTheOneKeptAtClosingLikeTheList() {
		// X 를 닫으며 x3 을 대표로 남겼다 — 닫힌 사건은 표현이 없어 전체 기사에서 다시 고르면 가장 먼저 발행한 x1 이 된다
		this.jdbc.sql("""
				update events set status = 'CLOSED', closed_at = '2026-11-07T00:00:00Z', representative_article_id = :x3
				where id = :event
				""").param("x3", this.x3).param("event", this.eventX).update();
		List<ArticleId> all = List.of(new ArticleId(this.x1), new ArticleId(this.x2), new ArticleId(this.x3),
				new ArticleId(this.x4));

		assertThat(this.directory.representativesOfAll(List.of(new EventId(this.eventX), new EventId(this.eventY))))
			.containsOnly(Map.entry(new EventId(this.eventX), new ArticleId(this.x3)),
					// 열린 Y 는 남긴 대표가 없다 — 같은 시각에 발행한 두 기사 가운데 ID 가 작은 y1
					Map.entry(new EventId(this.eventY), new ArticleId(this.y1)));
		assertThat(this.directory.representatives(Map.of(new EventId(this.eventX), all)))
			.containsExactly(Map.entry(new EventId(this.eventX), new ArticleId(this.x1)));
		assertThat(this.directory.list(PERIOD, null, 1).events().getFirst().representative())
			.isEqualTo(new ArticleId(this.x3));
		assertThat(this.directory.representativesOfAll(List.of())).isEmpty();
		assertThat(this.directory.representativesOfAll(List.of(new EventId(999_999)))).isEmpty();
	}

	private void clear() {
		EventArticles.clear(this.jdbc);
		if (!this.evaluated.isEmpty()) {
			this.jdbc.sql("delete from article_evaluations where article_id = any(:ids)")
				.param("ids", this.evaluated.toArray(Long[]::new))
				.update();
			this.evaluated.clear();
		}
	}

	private long article(long source, @Nullable Double importance) {
		long article = EventArticles.insert(this.jdbc, "사건 조회 시험 기사", null, source, 10 + source);
		if (importance != null) {
			this.jdbc.sql("""
					insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
					    format_distribution, importance, importance_distribution, evaluated_at)
					values (:article, (select id from tag_sets order by number desc limit 1), :article, 'straight', 1.0,
					    '{}', :importance, '{}', '2026-11-05T00:00:00Z')
					""").param("article", article).param("importance", importance).update();
			this.evaluated.add(article);
		}
		return article;
	}

	private long event(String first, String last) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast('[1,0]' as halfvec), 4, :first, :last, :first, :first)
				returning id
				""")
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
	}

	private long assignmentCriteria() {
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
	}

	private void assign(long article, long event, long criteriaId, String published) {
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, 0.9, 'FORMULA', false, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("article", article)
			.param("event", event)
			.param("published", Instant.parse(published).atOffset(ZoneOffset.UTC))
			.param("criteria", criteriaId)
			.update();
	}

}
