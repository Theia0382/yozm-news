package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventClosing;
import net.yozmnews.event.domain.event.EventQueryRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.LongTermIndexes;
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
 * 사건 정리 — 마지막으로 붙은 뒤 닫는 기간(7일) 또는 연 뒤 최대 기간(14일)이 지난 열린 사건을 대표 기사와 함께 닫고, 닫힌 사건 기사의 표현과
 * 만든 뒤 창 + 1일이 지난 배정 없는 표현을 지운다. 지금은 2026-11-20T00:00Z, 창 72시간이다. 사건 · 배정 · 표현은 SQL 로 넣는다. 인덱스는
 * 대체물(만든 수 0)로 둔다 — 인덱스는 {@code LongTermIndexesTests} 가 본다.
 * <p>
 * 지키는 규칙: event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class CleanupFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	private static final EventClosing CLOSING = EventClosing.of(Duration.ofDays(7), Duration.ofDays(14),
			CRITERIA.window());

	@Autowired
	EventRepository events;

	@Autowired
	EventArticleRepository assignments;

	@Autowired
	EventQueryRepository queries;

	@Autowired
	MatchFeaturesRepository features;

	@Autowired
	AssignmentQueueRepository queue;

	@Autowired
	IntakeQueueRepository intake;

	@Autowired
	CriteriaRepository criteriaHistory;

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
	}

	@Test
	void anIdleEventClosesWithItsRepresentative() {
		// 마지막으로 붙은 지 8일 — 닫는다. 대표는 중심 [1,0] 에 가장 가까운 기사
		Event idle = event("[1,0]", "2026-11-12T00:00:00Z", "2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z", "[0.6,0.8]",
				"[1,0]");
		// 마지막으로 붙은 지 6일 — 열려 있다
		Event recent = event("[0,1]", "2026-11-14T00:00:00Z", "2026-11-12T00:00:00Z", "2026-11-14T00:00:00Z", "[0,1]");
		// 마지막 기사 발행은 12일 전이지만 어제 붙었다(늦게 처리된 기사) — 열려 있다
		Event late = event("[0,1]", "2026-11-08T00:00:00Z", "2026-11-15T00:00:00Z", "2026-11-19T00:00:00Z", "[0,1]");
		// 연 지 15일 — 어제 붙었어도 닫는다
		Event old = event("[0,1]", "2026-11-19T00:00:00Z", "2026-11-05T00:00:00Z", "2026-11-19T00:00:00Z", "[0,1]");

		cleanup().run();

		assertThat(row(idle.id())).containsEntry("status", "CLOSED")
			.containsEntry("closed", "2026-11-20T00:00:00Z")
			.containsEntry("representative_article_id", idle.members().get(1));
		assertThat(row(recent.id())).containsEntry("status", "OPEN").containsEntry("closed", null);
		assertThat(row(late.id())).containsEntry("status", "OPEN");
		assertThat(row(old.id())).containsEntry("status", "CLOSED")
			.containsEntry("representative_article_id", old.members().get(0));
		// 대표를 고른 뒤 닫힌 사건 기사의 표현을 지웠다 — 열린 사건 기사의 표현은 남는다
		assertThat(featureArticles()).containsExactlyInAnyOrder(recent.members().get(0), late.members().get(0));
	}

	@Test
	void anEventWithoutRepresentationsClosesWithItsEarliestArticle() {
		// 두 기사 모두 표현이 없다(보존 기간이 지나 지움) — 중심에 가까운 순으로는 고를 수 없어 발행 시각 · 기사 ID 순 첫째가 대표다
		Event bare = event("[1,0]", "2026-11-12T00:00:00Z", "2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z", "[0,1]",
				"[1,0]");
		this.jdbc.sql("delete from article_match_features").update();

		cleanup().run();

		assertThat(row(bare.id())).containsEntry("status", "CLOSED")
			.containsEntry("representative_article_id", bare.members().get(0));
	}

	@Test
	void aClosedEventIsNoCandidateForANewArticle() {
		Event idle = event("[1,0]", "2026-11-12T00:00:00Z", "2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z", "[1,0]");
		cleanup().run();
		long article = queued("[1,0]", "2026-11-12T00:00:00Z");

		assign();

		long event = this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
		assertThat(event).isNotEqualTo(idle.id());
		assertThat(row(idle.id())).containsEntry("article_count", 1);
	}

	@Test
	void featuresOfClosedEventsAndStaleUnassignedRowsAreDeleted() {
		Event closed = event("[1,0]", "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", "[1,0]");
		this.jdbc.sql("update events set status = 'CLOSED', closed_at = now() where id = :id")
			.param("id", closed.id())
			.update();
		// 열린 사건에 배정된 기사의 표현은 오래되어도 남는다
		Event open = event("[0,1]", "2026-11-19T00:00:00Z", "2026-11-15T00:00:00Z", "2026-11-19T00:00:00Z", "[0,1]");
		this.jdbc.sql("update article_match_features set created_at = '2026-11-01T00:00:00Z'").update();
		// 같은 기사의 옛 모델 버전 표현은 그 모델 버전의 열린 사건에 배정되지 않았다 — 오래되었으면 지운다
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, 'model-old', cast('[0,1]' as vector), '{}', '2026-11-01T00:00:00Z')
				""").param("article", open.members().get(0)).update();
		// 배정 없는 표현 — 만든 시각이 지금 − (72시간 + 1일) = 11-16T00:00Z 보다 이르면 지운다
		long stale = represented("[1,0]", "2026-11-15T23:59:59Z");
		long fresh = represented("[1,0]", "2026-11-16T00:00:01Z");
		// 배정 대기에 있는 기사의 표현도 같은 기준으로 지운다 — 배정 회차가 다시 만든다
		long waiting = represented("[1,0]", "2026-11-15T23:59:59Z");
		this.queue.enqueue(new ArticleId(waiting), NOW);

		cleanup().run();

		assertThat(featureArticles()).containsExactlyInAnyOrder(open.members().get(0), fresh);
		assertThat(stale).isNotIn(featureArticles());
		assertThat(this.jdbc.sql("select model_version from article_match_features where article_id = :article")
			.param("article", open.members().get(0))
			.query(String.class)
			.list()).containsExactly("model-a");

		assign();

		assertThat(this.jdbc.sql("select article_id from event_intake_queue").query(Long.class).list())
			.containsExactly(waiting);
		assertThat(this.assignments.exists(new ArticleId(waiting))).isFalse();
	}

	@Test
	void runningAgainChangesNothing() {
		event("[1,0]", "2026-11-12T00:00:00Z", "2026-11-10T00:00:00Z", "2026-11-12T00:00:00Z", "[0.6,0.8]", "[1,0]");
		event("[0,1]", "2026-11-14T00:00:00Z", "2026-11-12T00:00:00Z", "2026-11-14T00:00:00Z", "[0,1]");
		represented("[1,0]", "2026-11-15T23:59:59Z");
		cleanup().run();
		List<Map<String, Object>> before = snapshot();

		cleanup().run();

		assertThat(snapshot()).isEqualTo(before);
	}

	private CleanupRound cleanup() {
		LongTermIndexes none = () -> 0;
		return new CleanupRound(this.events, this.queries, this.features, none, CLOSING, CRITERIA, this.transaction,
				(InstantSource) () -> NOW);
	}

	private void assign() {
		new AssignmentRound(this.queue, this.intake, this.features, this.events, this.assignments, this.criteriaHistory,
				CRITERIA, this.articles, query -> new MembershipJudgement.Judged(Map.of()), this.transaction,
				(InstantSource) () -> NOW)
			.run(new ModelOnly());
	}

	/**
	 * 열린 사건 하나를 SQL 로 넣는다 — 구성 기사마다 기사(발행 = 마지막 기사 시각) · 표현(모델 a) · 배정.
	 * @param lastArticleAt 첫 · 마지막 기사 시각
	 * @param openedAt 연 시각
	 * @param joinedAt 마지막으로 붙은 시각
	 */
	private Event event(String centroid, String lastArticleAt, String openedAt, String joinedAt, String... vectors) {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast(:centroid as halfvec), :count, :last, :last, :opened, :joined) returning id
				""")
			.param("centroid", centroid)
			.param("count", vectors.length)
			.param("last", time(lastArticleAt))
			.param("opened", time(openedAt))
			.param("joined", time(joinedAt))
			.query(Long.class)
			.single();
		long criteria = this.criteriaHistory.register(CRITERIA, NOW);
		List<Long> members = new ArrayList<>();
		for (int i = 0; i < vectors.length; i++) {
			long article = represented(vectors[i], openedAt);
			this.jdbc.sql("""
					insert into event_articles (article_id, event_id, published_at, decided_by, opened, criteria_id,
					                            assigned_at)
					values (:article, :event, :published, 'FORMULA', :opened, :criteria, :joined)
					""")
				.param("article", article)
				.param("event", event)
				.param("published", time(lastArticleAt))
				.param("opened", i == 0)
				.param("criteria", criteria)
				.param("joined", time(joinedAt))
				.update();
			members.add(article);
		}
		return new Event(event, members);
	}

	/** 표현(모델 a)이 있는 기사 하나 */
	private long represented(String vector, String createdAt) {
		long article = EventArticles.insert(this.jdbc, "정리 시험 기사", null);
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, 'model-a', cast(:vector as vector), '{}', :created)
				""").param("article", article).param("vector", vector).param("created", time(createdAt)).update();
		return article;
	}

	/** 배정 대기에 넣은 기사 하나 — 발행 시각을 고른다 */
	private long queued(String vector, String publishedAt) {
		long article = represented(vector, "2026-11-19T00:00:00Z");
		this.jdbc.sql("update articles set published_at = :published where id = :article")
			.param("published", time(publishedAt))
			.param("article", article)
			.update();
		this.queue.enqueue(new ArticleId(article), NOW);
		return article;
	}

	private Map<String, @Nullable Object> row(long event) {
		return this.jdbc.sql("""
				select status, article_count, representative_article_id,
				    to_char(closed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as closed
				from events where id = :event
				""").param("event", event).query().singleRow();
	}

	private List<Long> featureArticles() {
		return this.jdbc.sql("select article_id from article_match_features").query(Long.class).list();
	}

	private List<Map<String, Object>> snapshot() {
		List<Map<String, Object>> rows = new ArrayList<>();
		rows.addAll(this.jdbc.sql("select * from events order by id").query().listOfRows());
		rows.addAll(this.jdbc.sql("""
				select article_id, model_version, created_at from article_match_features order by article_id, model_version
				""").query().listOfRows());
		return rows;
	}

	private static java.time.OffsetDateTime time(String instant) {
		return Instant.parse(instant).atOffset(ZoneOffset.UTC);
	}

	private record Event(long id, List<Long> members) {
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

}
