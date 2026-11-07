package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventAdministration;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaRows;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventSummary;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.domain.bigevent.BigEventCriteriaRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 조회와 큰 사건 판정의 흐름 — 기사 · 엔트리 · 평가 · 사건 · 배정 행을 SQL 로 넣고, 공개 조회가 다른 모듈의 공개 조회로 소스 · 중요도를
 * 읽어 기간 안에서 판정하는지, 기준을 바꾸면 다음 조회부터 새 기준으로 판정하는지, 미리보기가 아무것도 쓰지 않는지, 사건 목록의 쪽 넘기기를
 * 실제 PostgreSQL 에서 본다.
 * <p>
 * 기준은 인스턴스에 한 행이라 시작과 끝에 비우고 처음 값을 다시 적재한다. 사건 · 기사 행과 이 시험이 넣은 평가 행도 시작과 끝에 지운다.
 * <p>
 * 지키는 규칙: event-R14 · event-R15 · event-R20 · event-R21
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventQueryFlowTests {

	/** 기간 [11-05T00, 11-06T00) */
	private static final EventPeriod PERIOD = new EventPeriod(Instant.parse("2026-11-05T00:00:00Z"),
			Instant.parse("2026-11-06T00:00:00Z"));

	@Autowired
	EventDirectory directory;

	@Autowired
	BigEventAdministration administration;

	@Autowired
	BigEventCriteriaRepository criteria;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> evaluated = new ArrayList<>();

	private long x1;

	private long x2;

	private long x3;

	private long x4;

	private long eventX;

	private long eventY;

	@BeforeEach
	void startFromTheFixture() {
		clear();
		new BigEventCriteriaSeeding(this.criteria, this.transaction, InstantSource.system()).seedIfAbsent();
		long criteriaId = assignmentCriteria();
		// 사건 Y 를 먼저 연다 — 사건 ID 순과 큰 사건 순서(소스 수 내림차순)가 어긋난다
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
		assign(article(1, 5.0), this.eventY, criteriaId, "2026-11-05T15:00:00Z");
		assign(article(2, 5.0), this.eventY, criteriaId, "2026-11-05T15:00:00Z");
	}

	@AfterEach
	void endWithTheSeededFirstValue() {
		clear();
		new BigEventCriteriaSeeding(this.criteria, this.transaction, InstantSource.system()).seedIfAbsent();
	}

	@Test
	void bigEventsAreJudgedWithinThePeriodWithTheSourcesAndImportancesOfOtherModules() {
		assertThat(this.directory.bigEvents(PERIOD)).containsExactly(new BigEvent(new EventId(this.eventX),
				List.of(new ArticleId(this.x1), new ArticleId(this.x2), new ArticleId(this.x3)),
				new EventTally(3, 3, new BigDecimal("3.5"))));
	}

	@Test
	void aChangedCriterionAppliesToTheNextQueryAndThePreviewWritesNothing() {
		this.administration.change(4, new BigDecimal("3.00"), ChangeActor.OPERATOR);

		assertThat(this.directory.bigEvents(PERIOD)).isEmpty();
		assertThat(this.administration.preview(new BigEventCriteria(3, new BigDecimal("3.00")), PERIOD))
			.extracting(event -> event.id().value())
			.containsExactly(this.eventX);
		// 둘 다 큰 사건인 제안 기준 — 소스 셋인 X 가 소스 둘인 Y 보다 앞선다(사건 ID 는 Y 가 작다)
		assertThat(this.administration.preview(new BigEventCriteria(2, new BigDecimal("3.00")), PERIOD))
			.extracting(event -> event.id().value())
			.containsExactly(this.eventX, this.eventY);
		assertThat(this.administration.criteria()).isEqualTo(new BigEventCriteria(4, new BigDecimal("3.00")));
		assertThat(this.administration.changes()).hasSize(2);
	}

	@Test
	void theListPagesEventsWithTheirPeriodTallyBigFlagAndRepresentative() {
		EventPage first = this.directory.list(PERIOD, null, 1);

		// X 의 마지막 기사 시각(11-06T00)이 Y(11-05T15)보다 늦다. 대표 기사는 사건 기사 전체에서 — 표현이 없어 가장 먼저 발행한 x1
		assertThat(first.events()).hasSize(1);
		EventSummary x = first.events().getFirst();
		assertThat(x.event().id()).isEqualTo(new EventId(this.eventX));
		assertThat(x.tally()).isEqualTo(new EventTally(3, 3, new BigDecimal("3.5")));
		assertThat(x.big()).isTrue();
		assertThat(x.representative()).isEqualTo(new ArticleId(this.x1));
		assertThat(first.next()).isNotNull();

		EventPage second = this.directory.list(PERIOD, first.next(), 1);
		assertThat(second.events()).extracting(summary -> summary.event().id()).containsExactly(new EventId(this.eventY));
		assertThat(second.events().getFirst().tally()).isEqualTo(new EventTally(2, 2, new BigDecimal("5.0")));
		assertThat(second.events().getFirst().big()).isFalse();
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

	private void clear() {
		EventArticles.clear(this.jdbc);
		if (!this.evaluated.isEmpty()) {
			this.jdbc.sql("delete from article_evaluations where article_id = any(:ids)")
				.param("ids", this.evaluated.toArray(Long[]::new))
				.update();
			this.evaluated.clear();
		}
		BigEventCriteriaRows.removeAll(this.jdbc);
	}

	private long article(long source, @Nullable Double importance) {
		long article = EventArticles.insert(this.jdbc, "사건 조회 시험 기사", null, source, 10 + source);
		if (importance != null) {
			this.jdbc.sql("""
					insert into article_evaluations (article_id, tag_set_id, evaluator, model_version, format_key,
					    format_probability, format_distribution, importance, importance_distribution, raw_response,
					    input_tokens, evaluated_at)
					values (:article, (select id from tag_sets order by number desc limit 1), 'fake', 'fake-1', 'straight',
					    1.0, '{}', :importance, '{}', '{}', 0, '2026-11-05T00:00:00Z')
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
