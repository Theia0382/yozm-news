package net.yozmnews.event.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.EmbeddingResult;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
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
 * 같은 사건에 동시에 붙이기 — 첫 회차가 사건을 잠근 채 커밋 직전에 멈춘 동안 둘째 회차는 끝나지 않고(기다림을 관측한다), 첫째가 커밋한 뒤
 * 둘째가 갱신된 중심 · 수 위에서 더한다. 잠금이 없으면 둘째가 옛 중심 · 수로 덮어써 기사 하나가 빠진다.
 * <p>
 * 사건 E 는 기사 하나([1,0])로 시작한다. 첫째 회차의 기사는 [1,0], 둘째 회차의 기사는 [4,3] 이고 둘 다 핵심 말 {@code 금리} 가 사건과
 * 겹쳐 산식으로 붙는다(1.0 · 0.86). 기대 중심은 ([1,0] + [1,0] + [4,3]) / 3 = [2,1], 수 3.
 * <p>
 * 지키는 규칙: event-R12 · event-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventAssignmentConcurrencyTests {

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
	void aSecondAttachmentWaitsForTheFirstAndAddsOnTopOfIt() throws Exception {
		long seed = article("[1,0]", NOW);
		round(this.assignments, NOW).run(new ModelOnly());
		long event = this.jdbc.sql("select event_id from event_articles where article_id = :a")
			.param("a", seed)
			.query(Long.class)
			.single();
		long first = article("[1,0]", NOW);
		// 둘째 기사는 1초 뒤에 판정할 수 있다 — 첫째 회차가 점유하지 않는다
		long second = article("[4,3]", NOW.plusSeconds(1));
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		Pausing pausing = new Pausing(this.assignments, paused, resume);

		CompletableFuture<Void> firstRound = CompletableFuture.runAsync(() -> round(pausing, NOW).run(new ModelOnly()));
		try {
			assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Void> secondRound = CompletableFuture
				.runAsync(() -> round(this.assignments, NOW.plusSeconds(1)).run(new ModelOnly()));
			Thread.sleep(500);
			assertThat(secondRound).as("첫 회차가 사건을 잡은 동안 둘째 회차가 기다린다").isNotDone();
			resume.countDown();
			firstRound.get(10, TimeUnit.SECONDS);
			secondRound.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(this.jdbc.sql("select event_id from event_articles where article_id in (:a, :b)")
			.param("a", first)
			.param("b", second)
			.query(Long.class)
			.list()).containsOnly(event).hasSize(2);
		assertThat(this.jdbc.sql("select centroid::text as centroid, article_count from events where id = :event")
			.param("event", event)
			.query()
			.singleRow()).isEqualTo(Map.of("centroid", "[2,1]", "article_count", 3));
		assertThat(this.jdbc.sql("select article_count from event_key_terms where event_id = :event and term = '금리'")
			.param("event", event)
			.query(Integer.class)
			.single()).isEqualTo(3);
	}

	private AssignmentRound round(EventArticleRepository assignments, Instant now) {
		return new AssignmentRound(this.queue, this.intake, this.features, this.events, assignments,
				this.criteriaHistory, CRITERIA, this.articles,
				query -> new MembershipJudgement.Judged(Map.of()), this.transaction, (InstantSource) () -> now);
	}

	private long article(String vector, Instant availableAt) {
		long article = EventArticles.insert(this.jdbc, "동시 배정 시험 기사", null);
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, 'model-a', cast(:vector as vector), '{금리}', :now)
				""").param("article", article).param("vector", vector).param("now", NOW.atOffset(ZoneOffset.UTC)).update();
		this.queue.enqueue(new ArticleId(article), availableAt);
		return article;
	}

	private record ModelOnly() implements ArticleEmbedder {

		@Override
		public EmbedderInfo info() {
			return new EmbedderInfo("시험", "model-a", 2);
		}

		@Override
		public EmbeddingResult embed(List<String> texts) {
			throw new AssertionError("배정 회차는 임베딩하지 않는다");
		}

	}

	/** 배정 행을 넣기 직전(사건을 잠그고 고친 뒤) 신호를 보내고 풀릴 때까지 기다린다 — 쓰기 트랜잭션을 커밋 전에 세운다 */
	private static final class Pausing extends ForwardingEventArticleRepository {

		private final CountDownLatch paused;

		private final CountDownLatch resume;

		Pausing(EventArticleRepository delegate, CountDownLatch paused, CountDownLatch resume) {
			super(delegate);
			this.paused = paused;
			this.resume = resume;
		}

		@Override
		public boolean insertIfAbsent(EventArticle row, List<StoredCandidate> stored) {
			this.paused.countDown();
			awaitQuietly(this.resume);
			return super.insertIfAbsent(row, stored);
		}

	}

}
