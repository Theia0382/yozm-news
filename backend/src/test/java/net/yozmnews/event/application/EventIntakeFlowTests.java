package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.adapter.out.fake.FakeArticleEmbedder;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.KeyTermExtractor;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 묶기 흐름 — 새 기사 → 대기 → 회차 → 기사 표현, 다시 받기, 지운 기사 따르기와 엇갈림, 임베더 실패를 실제 PostgreSQL 과 결선된
 * 저장소 · 공개 조회로 본다.
 * <p>
 * 회차는 시험이 고정 시계와 임베더를 넣어 직접 만든다 — 작업 서버를 꺼 반복 작업이 시험의 회차와 겹치지 않고, profile {@code local} 이
 * 없어 임베더 빈은 없다. 회차가 넘긴 텍스트는 대체물을 감싼 기록 임베더로 보고, 벡터는 {@code embedding::text} 로 되읽는다. 시험 문장은
 * 지어낸 것이다.
 * <p>
 * 지운 기사 따르기는 리스너를 직접 부르는 시험과 별도로 소스 삭제 이벤트를 발행해 {@code ArticlesRemoved} 가 발행 레지스트리를 거쳐 오는
 * 길로도 본다.
 * <p>
 * 지키는 규칙: event-R1 · event-R2 · event-R3 · event-R4 · event-R5 · event-R6 · event-R8
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class EventIntakeFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-05T01:00:00Z");

	private static final String TITLE = "한국은행 기준금리 동결";

	private static final String SUMMARY = "한국은행이 기준금리를 연 3.25%로 동결했다.";

	@Autowired
	IntakeQueueRepository queue;

	@Autowired
	MatchFeaturesRepository features;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	KeyTermExtractor keyTerms;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void aStoredArticleIsQueuedAndThenRepresentedFromItsTitleAndSummary() {
		long article = EventArticles.insert(this.jdbc, TITLE, SUMMARY);
		Recording embedder = new Recording();

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ArticleStored(new ArticleId(article))));
		await().atMost(Duration.ofSeconds(10)).until(() -> queueRows() == 1);
		round().run(embedder);

		assertThat(embedder.texts).containsExactly("한국은행 기준금리 동결\n한국은행이 기준금리를 연 3.25%로 동결했다.");
		assertThat(this.jdbc.sql("""
				select model_version, vector_dims(embedding) as dims, entities::text as entities,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created
				from article_match_features where article_id = :article
				""").param("article", article).query().singleRow())
			.containsEntry("model_version", "fake-char-bigram-256-1")
			.containsEntry("dims", 256)
			.containsEntry("entities", "{한국은행,기준,금리,동결}")
			.containsEntry("created", "2026-11-05T01:00:00Z");
		assertThat(queueRows()).isZero();
	}

	@Test
	void anArticleWithoutASummaryIsRepresentedFromItsTitleOnly() {
		long titleOnly = enqueued(TITLE, null);
		long withSummary = enqueued(TITLE, SUMMARY);
		Recording embedder = new Recording();

		round().run(embedder);

		assertThat(embedder.texts).containsExactlyInAnyOrder("한국은행 기준금리 동결",
				"한국은행 기준금리 동결\n한국은행이 기준금리를 연 3.25%로 동결했다.");
		assertThat(storedVector(titleOnly)).containsExactly(fake("한국은행 기준금리 동결"));
		assertThat(storedVector(withSummary)).isNotEqualTo(storedVector(titleOnly));
	}

	@Test
	void aVeryLongSummaryIsCutTo300CodePoints() {
		enqueued("긴 요약 기사", "나".repeat(1000));
		Recording embedder = new Recording();

		round().run(embedder);

		assertThat(embedder.texts).containsExactly("긴 요약 기사\n" + "나".repeat(300));
	}

	@Test
	void receivingTheSameArticleAgainKeepsOneRepresentationWithTheSameValues() {
		long article = EventArticles.insert(this.jdbc, TITLE, SUMMARY);
		EventIntakeListener listener = listener();
		ArticleStored stored = new ArticleStored(new ArticleId(article));

		this.transaction.executeWithoutResult(status -> listener.on(stored));
		this.transaction.executeWithoutResult(status -> listener.on(stored));
		assertThat(queueRows()).isEqualTo(1);
		round().run(new FakeArticleEmbedder());
		float[] first = storedVector(article);
		this.transaction.executeWithoutResult(status -> listener.on(stored));
		round().run(new FakeArticleEmbedder());

		assertThat(featureRows()).isEqualTo(1);
		assertThat(storedVector(article)).containsExactly(first);
		assertThat(queueRows()).isZero();
	}

	@Test
	void removingArticlesRemovesTheirQueueRowsAndRepresentationsOnly() {
		long removed = enqueued(TITLE, SUMMARY);
		long kept = enqueued("축구 결승 진출", null);
		round().run(new FakeArticleEmbedder());
		this.queue.enqueue(new ArticleId(removed), NOW);
		EventIntakeListener listener = listener();
		ArticlesRemoved event = new ArticlesRemoved(List.of(new ArticleId(removed)));

		this.transaction.executeWithoutResult(status -> listener.on(event));
		this.transaction.executeWithoutResult(status -> listener.on(event));

		assertThat(this.jdbc.sql("select article_id from article_match_features").query(Long.class).list())
			.containsExactly(kept);
		assertThat(queueRows()).isZero();
	}

	@Test
	void deletingASourceRemovesTheQueueRowsAndRepresentationsOfItsArticlesThroughTheEvents() {
		long gone = EventArticles.insert(this.jdbc, TITLE, SUMMARY, 31, 311);
		long kept = EventArticles.insert(this.jdbc, "축구 결승 진출", null, 32, 321);
		this.queue.enqueue(new ArticleId(gone), NOW);
		this.queue.enqueue(new ArticleId(kept), NOW);
		round().run(new FakeArticleEmbedder());
		this.queue.enqueue(new ArticleId(gone), NOW);
		this.queue.enqueue(new ArticleId(kept), NOW);

		// source → article(SourceDeleted) → event(ArticlesRemoved) — 리스너를 직접 부르지 않고 발행 레지스트리를 거친다
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new SourceDeleted(new SourceId(31))));
		await().atMost(Duration.ofSeconds(10))
			.until(() -> this.jdbc.sql("select article_id from article_match_features").query(Long.class).list()
				.equals(List.of(kept)) && queueRows() == 1);

		assertThat(this.jdbc.sql("select id from articles").query(Long.class).list()).containsExactly(kept);
		assertThat(this.jdbc.sql("select article_id from event_intake_queue").query(Long.class).list())
			.containsExactly(kept);
	}

	@Test
	void aRepresentationIsDiscardedWhenTheArticleIsRemovedDuringItsEmbedding() {
		long removed = enqueued(TITLE, SUMMARY);
		long kept = enqueued("축구 결승 진출", null);
		EventIntakeListener listener = listener();
		ArticleEmbedder removing = new Recording() {

			@Override
			public List<Embedding> embed(List<String> texts) {
				EventIntakeFlowTests.this.transaction.executeWithoutResult(
						status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(removed)))));
				return super.embed(texts);
			}

		};

		round().run(removing);

		assertThat(this.jdbc.sql("select article_id from article_match_features").query(Long.class).list())
			.containsExactly(kept);
		assertThat(queueRows()).isZero();
	}

	@Test
	void aRemovalWaitsForAnInFlightWriteAndThenRemovesItsRepresentation() throws Exception {
		long article = enqueued(TITLE, SUMMARY);
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		MatchFeaturesRepository pausing = new Pausing(this.features, inserted, release);
		EventIntakeListener listener = listener();
		try {
			CompletableFuture<Void> writing = CompletableFuture
				.runAsync(() -> round(pausing).run(new FakeArticleEmbedder()));
			assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
			// 쓰기 트랜잭션이 대기 행을 지우고 표현 행을 넣은 채 커밋 전이다 — 지우기는 대기 행에서 기다린다
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> this.transaction
				.executeWithoutResult(status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(article))))));
			Thread.sleep(300);
			boolean waited = !removing.isDone();
			release.countDown();
			writing.get(10, TimeUnit.SECONDS);
			removing.get(10, TimeUnit.SECONDS);

			assertThat(waited).isTrue();
			assertThat(featureRows()).isZero();
			assertThat(queueRows()).isZero();
		}
		finally {
			release.countDown();
		}
	}

	@Test
	void aQueuedArticleThatNoLongerExistsLeavesTheQueueWithoutCallingTheEmbedder(CapturedOutput output) {
		this.queue.enqueue(new ArticleId(999_999), NOW);
		Recording embedder = new Recording();

		round().run(embedder);

		assertThat(embedder.texts).isEmpty();
		assertThat(queueRows()).isZero();
		assertThat(featureRows()).isZero();
		assertThat(output).contains("사건 묶기 대기 기사가 지워져 대기에서 뺐다 — 기사 999999");
	}

	@Test
	void aFailingEmbedderReleasesTheLeasesAndWritesNothing(CapturedOutput output) {
		long article = enqueued(TITLE, SUMMARY);
		ArticleEmbedder failing = new Recording() {

			@Override
			public List<Embedding> embed(List<String> texts) {
				throw new IllegalStateException("모델을 읽지 못했다");
			}

		};

		round().run(failing);

		assertThat(featureRows()).isZero();
		assertThat(waitingWithoutLease(article)).isTrue();
		assertThat(output).contains(
				"임베더가 실패해 회차를 멈췄다 — fake fake-char-bigram-256-1, 기사 1건: java.lang.IllegalStateException: 모델을 읽지 못했다");
	}

	@Test
	void anEmbedderResultWithAnotherDimensionReleasesTheLeasesAndWritesNothing(CapturedOutput output) {
		long article = enqueued(TITLE, SUMMARY);
		ArticleEmbedder mismatched = new ArticleEmbedder() {

			@Override
			public EmbedderInfo info() {
				return new EmbedderInfo("short", "short-1", 4);
			}

			@Override
			public List<Embedding> embed(List<String> texts) {
				return texts.stream().map(text -> Embedding.of(new float[] { 1f, 0f, 0f })).toList();
			}

		};

		round().run(mismatched);

		assertThat(featureRows()).isZero();
		assertThat(waitingWithoutLease(article)).isTrue();
		assertThat(output).contains(
				"임베더가 실패해 회차를 멈췄다 — short short-1, 기사 1건: 벡터 차원이 임베더가 밝힌 차원과 다르다 — 0번째 3, 밝힌 차원 4");
	}

	private EventIntakeRound round() {
		return round(this.features);
	}

	private EventIntakeRound round(MatchFeaturesRepository repository) {
		return new EventIntakeRound(this.queue, repository, this.articles, this.keyTerms, this.transaction,
				(InstantSource) () -> NOW);
	}

	private EventIntakeListener listener() {
		return new EventIntakeListener(this.queue, this.features, (InstantSource) () -> NOW);
	}

	private long enqueued(String title, @Nullable String summary) {
		long article = EventArticles.insert(this.jdbc, title, summary);
		this.queue.enqueue(new ArticleId(article), NOW);
		return article;
	}

	private static float[] fake(String text) {
		return new FakeArticleEmbedder().embed(List.of(text)).get(0).values();
	}

	private float[] storedVector(long article) {
		String text = this.jdbc.sql("select embedding::text from article_match_features where article_id = :article")
			.param("article", article)
			.query(String.class)
			.single();
		String[] parts = text.substring(1, text.length() - 1).split(",");
		float[] values = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Float.parseFloat(parts[i]);
		}
		return values;
	}

	private long queueRows() {
		return this.jdbc.sql("select count(*) from event_intake_queue").query(Long.class).single();
	}

	private long featureRows() {
		return this.jdbc.sql("select count(*) from article_match_features").query(Long.class).single();
	}

	private boolean waitingWithoutLease(long article) {
		return this.jdbc.sql("select count(*) from event_intake_queue where article_id = :article and lease_token is null")
			.param("article", article)
			.query(Long.class)
			.single() == 1;
	}

	/** 대체물을 감싸 회차가 넘긴 텍스트를 남기는 임베더 */
	private static class Recording implements ArticleEmbedder {

		private final FakeArticleEmbedder delegate = new FakeArticleEmbedder();

		final List<String> texts = new ArrayList<>();

		@Override
		public EmbedderInfo info() {
			return this.delegate.info();
		}

		@Override
		public List<Embedding> embed(List<String> texts) {
			this.texts.addAll(texts);
			return this.delegate.embed(texts);
		}

	}

	/** 표현 행을 넣은 뒤 트랜잭션 안에서 신호를 보내고 풀릴 때까지 기다린다 — 쓰기 트랜잭션을 커밋 직전에 세운다 */
	private static final class Pausing implements MatchFeaturesRepository {

		private final MatchFeaturesRepository delegate;

		private final CountDownLatch inserted;

		private final CountDownLatch release;

		Pausing(MatchFeaturesRepository delegate, CountDownLatch inserted, CountDownLatch release) {
			this.delegate = delegate;
			this.inserted = inserted;
			this.release = release;
		}

		@Override
		public boolean insertIfAbsent(MatchFeatures written) {
			boolean result = this.delegate.insertIfAbsent(written);
			this.inserted.countDown();
			try {
				this.release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return result;
		}

		@Override
		public void deleteByArticles(Collection<ArticleId> articleIds) {
			this.delegate.deleteByArticles(articleIds);
		}

	}

}
