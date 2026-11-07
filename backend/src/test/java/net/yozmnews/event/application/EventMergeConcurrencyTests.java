package net.yozmnews.event.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.MergeJudgementRepository;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 병합과 배정 · 지운 기사 따르기가 동시에 돌 때 — 병합은 배정 행 → 사건 순으로 잠그고, 잠근 뒤 사건의 기사 수가 잠근 배정 수와 다르면 합치지
 * 않는다. 기사가 사라지거나 두 사건에 붙지 않고, 서로 엇갈려 기다리지 않는다. 병합은 배정 행을 잠근 직후 멈춰 세운다.
 * <p>
 * 사건 L(기사 셋 [1,0], 금리)과 사건 S(기사 하나 [0.8,0.6], 금리)는 0.7 × 0.8 + 0.3 × 1 = 0.86 으로 산식 병합 대상이다.
 * <p>
 * 지키는 규칙: event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class EventMergeConcurrencyTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

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
	MergeJudgementRepository judgements;

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

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void anArticleAttachedWhileTheMergeHoldsTheAssignmentsIsNotLost(CapturedOutput output) throws Exception {
		long large = event("[1,0]", "[1,0]", "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", "[0.8,0.6]");
		// S 와 같은 벡터 — S(1.0)가 L(0.86)보다 높아 S 에 붙는다
		long late = queued("[0.8,0.6]");
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		Pausing pausing = new Pausing(this.assignments, paused, resume);

		CompletableFuture<Void> merging = CompletableFuture.runAsync(() -> merge(pausing).run());
		try {
			assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
			// 병합은 아직 사건을 잠그지 않았다 — 배정은 기다리지 않고 S 에 붙여 커밋한다
			CompletableFuture.runAsync(() -> assign().run(new ModelOnly())).get(10, TimeUnit.SECONDS);
			assertThat(eventOf(late)).isEqualTo(small);
			resume.countDown();
			merging.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		// L 차례에는 잠근 S 의 배정 수(1)가 S 의 기사 수(2)와 달라 합치지 않고 되돌렸다 — 같은 회차의 S 차례가 새 값으로 다시 합쳤다
		assertThat(output.getOut())
			.contains("합칠 사건이 닫혔거나 그새 바뀌어 다음 회차에 다시 본다 — 사건 " + large + " · " + small);
		assertThat(status(small)).isEqualTo("MERGED");
		assertThat(articleCount(large)).isEqualTo(5);
		// 기사마다 배정 하나 — 다섯 기사가 모두 남는 사건에 있다
		assertThat(this.jdbc.sql("select article_id from event_articles").query(Long.class).list()).hasSize(5)
			.doesNotHaveDuplicates()
			.contains(late);
		assertThat(this.jdbc.sql("select distinct event_id from event_articles").query(Long.class).list())
			.containsExactly(large);
	}

	@Test
	void aRemovalWaitsForTheMergeAndThenTakesTheArticleOutOfTheSurvivor() throws Exception {
		long large = event("[1,0]", "[1,0]", "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", "[0.8,0.6]");
		long removed = membersOf(small).get(0);
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		Pausing pausing = new Pausing(this.assignments, paused, resume);

		CompletableFuture<Void> merging = CompletableFuture.runAsync(() -> merge(pausing).run());
		try {
			assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> remove(removed));
			Thread.sleep(500);
			assertThat(removing).as("병합이 배정 행을 잡은 동안 지운 기사 따르기가 기다린다").isNotDone();
			resume.countDown();
			merging.get(10, TimeUnit.SECONDS);
			removing.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(status(small)).isEqualTo("MERGED");
		assertThat(articleCount(large)).isEqualTo(3);
		assertThat(this.assignments.exists(new ArticleId(removed))).isFalse();
		assertThat(this.jdbc.sql("select distinct event_id from event_articles").query(Long.class).list())
			.containsExactly(large);
	}

	private MergeRound merge(EventArticleRepository assignments) {
		return new MergeRound(this.events, assignments, this.judgements, this.criteriaHistory, CRITERIA,
				query -> new MembershipJudgement.Judged(Map.of()), this.transaction, (InstantSource) () -> NOW);
	}

	private AssignmentRound assign() {
		return new AssignmentRound(this.queue, this.intake, this.features, this.events, this.assignments,
				this.criteriaHistory, CRITERIA, this.articles, query -> new MembershipJudgement.Judged(Map.of()),
				this.transaction, (InstantSource) () -> NOW);
	}

	private void remove(long article) {
		EventIntakeListener listener = new EventIntakeListener(this.intake, this.queue, this.features, this.events,
				this.assignments, this.shrinks, () -> NOW);
		this.transaction
			.executeWithoutResult(status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(article)))));
	}

	/** 열린 사건 하나를 SQL 로 넣는다 — 구성 기사마다 기사 · 표현(모델 a, 금리) · 배정. 핵심 말 금리는 기사 수만큼 */
	private long event(String centroid, String... memberVectors) {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast(:centroid as halfvec), :count, :t, :t, :t, :t) returning id
				""")
			.param("centroid", centroid)
			.param("count", memberVectors.length)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into event_key_terms (event_id, term, article_count) values (:event, '금리', :count)")
			.param("event", event)
			.param("count", memberVectors.length)
			.update();
		long criteria = this.criteriaHistory.register(CRITERIA, T);
		for (int i = 0; i < memberVectors.length; i++) {
			long article = represented(memberVectors[i]);
			this.jdbc.sql("""
					insert into event_articles (article_id, event_id, published_at, decided_by, opened, criteria_id,
					                            assigned_at)
					values (:article, :event, :t, 'FORMULA', :opened, :criteria, :t)
					""")
				.param("article", article)
				.param("event", event)
				.param("t", T.atOffset(ZoneOffset.UTC))
				.param("opened", i == 0)
				.param("criteria", criteria)
				.update();
		}
		return event;
	}

	/** 배정 대기에 넣은 기사 하나 — 표현(모델 a, 금리) */
	private long queued(String vector) {
		long article = represented(vector);
		this.queue.enqueue(new ArticleId(article), NOW);
		return article;
	}

	private long represented(String vector) {
		long article = EventArticles.insert(this.jdbc, "동시 병합 시험 기사", null);
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, 'model-a', cast(:vector as vector), '{금리}', :t)
				""").param("article", article).param("vector", vector).param("t", T.atOffset(ZoneOffset.UTC)).update();
		return article;
	}

	private long eventOf(long article) {
		return this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private List<Long> membersOf(long event) {
		return this.jdbc.sql("select article_id from event_articles where event_id = :event order by article_id")
			.param("event", event)
			.query(Long.class)
			.list();
	}

	private String status(long event) {
		return this.jdbc.sql("select status from events where id = :event")
			.param("event", event)
			.query(String.class)
			.single();
	}

	private int articleCount(long event) {
		return this.jdbc.sql("select article_count from events where id = :event")
			.param("event", event)
			.query(Integer.class)
			.single();
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

	/** 병합이 배정 행을 잠근 직후(사건을 잠그기 전) 신호를 보내고 풀릴 때까지 기다린다 — 병합 트랜잭션을 그 자리에 세운다 */
	private static final class Pausing extends ForwardingEventArticleRepository {

		private final CountDownLatch paused;

		private final CountDownLatch resume;

		Pausing(EventArticleRepository delegate, CountDownLatch paused, CountDownLatch resume) {
			super(delegate);
			this.paused = paused;
			this.resume = resume;
		}

		@Override
		public List<EventArticle> lockByEvents(Collection<Long> eventIds) {
			List<EventArticle> locked = super.lockByEvents(eventIds);
			this.paused.countDown();
			awaitQuietly(this.resume);
			return locked;
		}

	}

}
