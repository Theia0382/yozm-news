package net.yozmnews.event.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.jspecify.annotations.Nullable;
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
 * 지운 기사 따르기 — {@code ArticlesRemoved} 를 받으면 대기 · 배정 · 후보 · 표현을 지우고 사건의 중심 · 수 · 첫 · 마지막 시각 · 핵심
 * 말을 다시 맞추며, 비는 사건은 그 사건을 가리키는 후보와 함께 지운다. 배정 쓰기와 지우기가 엇갈려도 지운 기사의 배정이 남지 않는다.
 * <p>
 * 사건 E 는 배정 회차로 만든다 — 기사 a([1,0], 금리 · 물가, 00:00 발행)가 열고, b([1,0], 금리, 01:00)와 c([4,3], 금리, 02:00)가
 * 산식으로 붙는다(0.97 · 0.84). 중심 [2,1] · 수 3 · 금리 3 · 물가 1. 벡터는 반 정밀도로 정확한 값이고, 수가 작아 반 정밀도 빼기의 오차가
 * 커지지 않는다. 회차 · 리스너는 시험이 고정 시계로 직접 만든다(작업 서버는 끈다).
 * <p>
 * 지키는 규칙: event-R8 · event-R12
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventRemovalFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	@Autowired
	AssignmentQueueRepository queue;

	@Autowired
	IntakeQueueRepository intake;

	@Autowired
	MatchFeaturesRepository features;

	@Autowired
	EventRepository events;

	@Autowired
	EventArticleRepository assignments;

	@Autowired
	CriteriaRepository criteriaHistory;

	@Autowired
	EventShrinkWriter shrinks;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	long a;

	long b;

	long c;

	long event;

	@BeforeEach
	void threeArticleEvent() {
		EventArticles.clear(this.jdbc);
		this.a = queued("[1,0]", "2026-11-05T00:00:00Z", "금리", "물가");
		this.b = queued("[1,0]", "2026-11-05T01:00:00Z", "금리");
		this.c = queued("[4,3]", "2026-11-05T02:00:00Z", "금리");
		round(this.assignments, unasked()).run(new ModelOnly());
		this.event = eventOf(this.a);
		assertThat(List.of(eventOf(this.b), eventOf(this.c))).containsOnly(this.event);
		assertThat(eventRow(this.event)).containsEntry("centroid", "[2,1]").containsEntry("article_count", 3);
	}

	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void removingTheLastPublishedArticleTakesItOutOfTheEvent() {
		this.intake.enqueue(new ArticleId(this.c), NOW);
		this.queue.enqueue(new ArticleId(this.c), NOW);

		remove(this.c);

		// ([2,1] × 3 − [4,3]) / 2 = [1,0]
		assertThat(eventRow(this.event)).containsEntry("centroid", "[1,0]")
			.containsEntry("article_count", 2)
			.containsEntry("first", "2026-11-05T00:00:00Z")
			.containsEntry("last", "2026-11-05T01:00:00Z");
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 2, "물가", 1));
		assertThat(this.assignments.exists(new ArticleId(this.c))).isFalse();
		assertThat(count("select count(*) from event_article_candidates where article_id = " + this.c)).isZero();
		assertThat(count("select count(*) from article_match_features where article_id = " + this.c)).isZero();
		assertThat(count("select count(*) from event_intake_queue")).isZero();
		assertThat(count("select count(*) from event_assignment_queue")).isZero();
	}

	@Test
	void removingTheFirstArticleMovesTheFirstTimeAndDropsItsOnlyTerm() {
		remove(this.a);

		// ([2,1] × 3 − [1,0]) / 2 = [2.5,1.5]
		assertThat(eventRow(this.event)).containsEntry("centroid", "[2.5,1.5]")
			.containsEntry("article_count", 2)
			.containsEntry("first", "2026-11-05T01:00:00Z")
			.containsEntry("last", "2026-11-05T02:00:00Z");
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 2));
	}

	@Test
	void removingTwoArticlesAtOnceCountsEachOfThem() {
		remove(this.a, this.c);

		assertThat(eventRow(this.event)).containsEntry("centroid", "[1,0]")
			.containsEntry("article_count", 1)
			.containsEntry("first", "2026-11-05T01:00:00Z")
			.containsEntry("last", "2026-11-05T01:00:00Z");
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 1));
	}

	@Test
	void anArticleWithoutARepresentationOnlyLowersTheCount() {
		this.jdbc.sql("delete from article_match_features where article_id = :article").param("article", this.b).update();

		remove(this.b);

		assertThat(eventRow(this.event)).containsEntry("centroid", "[2,1]")
			.containsEntry("article_count", 2)
			.containsEntry("first", "2026-11-05T00:00:00Z")
			.containsEntry("last", "2026-11-05T02:00:00Z");
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 3, "물가", 1));
	}

	@Test
	void anEmptiedEventGoesWithTheCandidatesPointingToIt() {
		// y([0,1], 금리, 03:00) 는 띠에 들어 판정 확률 0.1 로 새 사건을 열고, 후보 E 를 남긴다
		long y = queued("[0,1]", "2026-11-05T03:00:00Z", "금리");
		round(this.assignments, query -> new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(this.event, 0.1))).run(new ModelOnly());
		long other = eventOf(y);
		assertThat(other).isNotEqualTo(this.event);
		assertThat(count("select count(*) from event_article_candidates where article_id = " + y + " and event_id = "
				+ this.event)).isEqualTo(1);

		remove(this.a, this.b, this.c);
		remove(this.a, this.b, this.c);

		assertThat(count("select count(*) from events where id = " + this.event)).isZero();
		assertThat(count("select count(*) from event_key_terms where event_id = " + this.event)).isZero();
		assertThat(count("select count(*) from event_article_candidates where event_id = " + this.event)).isZero();
		assertThat(eventOf(y)).isEqualTo(other);
		assertThat(eventRow(other)).containsEntry("article_count", 1);
	}

	@Test
	void removingTheRepresentativeClearsIt() {
		this.jdbc.sql("""
				update events set status = 'CLOSED', closed_at = now(), representative_article_id = :a where id = :event
				""").param("a", this.a).param("event", this.event).update();

		remove(this.a);

		// 닫힌 사건은 남고 대표만 빈다 — 대표를 다시 고를 표현이 없을 수 있다
		assertThat(this.jdbc.sql("select status, article_count, representative_article_id from events where id = :event")
			.param("event", this.event)
			.query()
			.singleRow()).containsEntry("status", "CLOSED")
			.containsEntry("article_count", 2)
			.containsEntry("representative_article_id", null);
	}

	@Test
	void removingAnotherArticleKeepsTheRepresentative() {
		this.jdbc.sql("""
				update events set status = 'CLOSED', closed_at = now(), representative_article_id = :a where id = :event
				""").param("a", this.a).param("event", this.event).update();

		remove(this.c);

		assertThat(this.jdbc.sql("select representative_article_id from events where id = :event")
			.param("event", this.event)
			.query(Long.class)
			.single()).isEqualTo(this.a);
	}

	@Test
	void removingAllArticlesOfASurvivorDeletesItsMergedRows() {
		// 사건 X 가 E 에 합쳐졌다 — X 를 가리키는 저장 후보와 그 쌍의 병합 판정 기록이 있다
		long merged = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, merged_into, closed_at)
				values ('MERGED', 'model-a', '[0,1]', 1, now(), now(), now(), now(), :survivor, now()) returning id
				""").param("survivor", this.event).query(Long.class).single();
		this.jdbc.sql("""
				insert into event_article_candidates (article_id, event_id, score, probability)
				values (999, :merged, 0.6, null), (999, :survivor, 0.5, null)
				""").param("merged", merged).param("survivor", this.event).update();
		this.jdbc.sql("""
				insert into event_merge_judgements (event_low, event_high, probability, judged_at)
				values (:low, :high, 0.7, now())
				""").param("low", Math.min(merged, this.event)).param("high", Math.max(merged, this.event)).update();

		remove(this.a, this.b, this.c);

		assertThat(count("select count(*) from events where id in (" + this.event + ", " + merged + ")")).isZero();
		assertThat(count("select count(*) from event_article_candidates where article_id = 999")).isZero();
		assertThat(count("select count(*) from event_merge_judgements")).isZero();
	}

	@Test
	void oneRemovalOverTwoEventsShrinksOneAndDeletesTheOther() {
		// y([0,1], 금리, 03:00) 는 판정 확률 0.1 로 혼자 새 사건을 연다 — c 와 y 를 한 번에 지우면 E 는 줄고 y 의 사건은 빈다
		long y = queued("[0,1]", "2026-11-05T03:00:00Z", "금리");
		round(this.assignments, query -> new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(this.event, 0.1))).run(new ModelOnly());
		long other = eventOf(y);
		assertThat(other).isNotEqualTo(this.event);

		remove(this.c, y);

		assertThat(eventRow(this.event)).containsEntry("centroid", "[1,0]")
			.containsEntry("article_count", 2)
			.containsEntry("first", "2026-11-05T00:00:00Z")
			.containsEntry("last", "2026-11-05T01:00:00Z");
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 2, "물가", 1));
		assertThat(count("select count(*) from events where id = " + other)).isZero();
		assertThat(count("select count(*) from event_article_candidates where article_id = " + y)).isZero();
	}

	@Test
	void theSameRemovalTwiceGivesTheSameResult() {
		remove(this.c);
		remove(this.c);

		assertThat(eventRow(this.event)).containsEntry("centroid", "[1,0]").containsEntry("article_count", 2);
		assertThat(termCounts(this.event)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 2, "물가", 1));
	}

	@Test
	void aRemovalWaitsForAnInFlightAssignmentAndThenTakesItOut() throws Exception {
		long z = queued("[0,1]", "2026-11-01T00:00:00Z", "선거");
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		Pausing pausing = new Pausing(this.assignments, inserted, resume);
		try {
			CompletableFuture<Void> writing = CompletableFuture
				.runAsync(() -> round(pausing, unasked()).run(new ModelOnly()));
			assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
			// 쓰기 트랜잭션이 배정 대기 행을 지우고 새 사건 · 배정을 넣은 채 커밋 전이다 — 지우기는 대기 행에서 기다린다
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> remove(z));
			Thread.sleep(500);
			assertThat(removing).as("배정 쓰기가 대기 행을 잡은 동안 지우기가 기다린다").isNotDone();
			resume.countDown();
			writing.get(10, TimeUnit.SECONDS);
			removing.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(this.assignments.exists(new ArticleId(z))).isFalse();
		assertThat(count("select count(*) from events")).isEqualTo(1);
		assertThat(eventRow(this.event)).containsEntry("article_count", 3);
	}

	@Test
	void anAssignmentJudgedAfterARemovalWritesNothing() {
		// z([0,1], 금리, 02:00) 는 띠에 든다 — 판정하는 동안 지워진다
		long z = queued("[0,1]", "2026-11-05T02:00:00Z", "금리");
		EventMembership removing = query -> {
			remove(z);
			return new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(this.event, 0.9));
		};

		round(this.assignments, removing).run(new ModelOnly());

		assertThat(this.assignments.exists(new ArticleId(z))).isFalse();
		assertThat(eventRow(this.event)).containsEntry("article_count", 3).containsEntry("centroid", "[2,1]");
		assertThat(count("select count(*) from event_assignment_queue")).isZero();
	}

	private void remove(long... articles) {
		List<ArticleId> ids = Arrays.stream(articles).mapToObj(ArticleId::new).toList();
		EventIntakeListener listener = new EventIntakeListener(this.intake, this.queue, this.features, this.events,
				this.assignments, this.shrinks, () -> NOW);
		this.transaction.executeWithoutResult(status -> listener.on(new ArticlesRemoved(ids)));
	}

	private AssignmentRound round(EventArticleRepository assignments, EventMembership membership) {
		return new AssignmentRound(this.queue, this.intake, this.features, this.events, assignments,
				this.criteriaHistory, CRITERIA, this.articles, membership, this.transaction, (InstantSource) () -> NOW);
	}

	private static EventMembership unasked() {
		return query -> {
			throw new AssertionError("묻지 않아야 한다: " + query);
		};
	}

	private long queued(String vector, String publishedAt, String... terms) {
		long article = EventArticles.insert(this.jdbc, "지운 기사 시험 기사", null);
		this.jdbc.sql("update articles set published_at = :published where id = :article")
			.param("published", Instant.parse(publishedAt).atOffset(ZoneOffset.UTC))
			.param("article", article)
			.update();
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, 'model-a', cast(:vector as vector), :terms, :now)
				""")
			.param("article", article)
			.param("vector", vector)
			.param("terms", terms)
			.param("now", NOW.atOffset(ZoneOffset.UTC))
			.update();
		this.queue.enqueue(new ArticleId(article), NOW);
		return article;
	}

	private long eventOf(long article) {
		return this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private Map<String, @Nullable Object> eventRow(long event) {
		return this.jdbc.sql("""
				select centroid::text as centroid, article_count,
				    to_char(first_article_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as first,
				    to_char(last_article_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as last
				from events where id = :event
				""").param("event", event).query().singleRow();
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

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

	private record ModelOnly() implements ArticleEmbedder {

		@Override
		public EmbedderInfo info() {
			return new EmbedderInfo("시험", "model-a", 2);
		}

		@Override
		public List<Embedding> embed(List<String> texts) {
			throw new AssertionError("배정 회차는 임베딩하지 않는다");
		}

	}

	/** 배정 행을 넣은 뒤(커밋 전) 신호를 보내고 풀릴 때까지 기다린다 */
	private static final class Pausing extends ForwardingEventArticleRepository {

		private final CountDownLatch inserted;

		private final CountDownLatch resume;

		Pausing(EventArticleRepository delegate, CountDownLatch inserted, CountDownLatch resume) {
			super(delegate);
			this.inserted = inserted;
			this.resume = resume;
		}

		@Override
		public boolean insertIfAbsent(EventArticle row, List<StoredCandidate> stored) {
			boolean result = super.insertIfAbsent(row, stored);
			this.inserted.countDown();
			awaitQuietly(this.resume);
			return result;
		}

	}

}
