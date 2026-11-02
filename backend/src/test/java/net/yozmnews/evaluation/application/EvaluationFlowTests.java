package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.ArticleEvaluationView;
import net.yozmnews.evaluation.ArticleEvaluations;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.EvaluationAdministration;
import net.yozmnews.evaluation.EvaluationQueueCounts;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicStatus;
import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 평가 흐름 — 대기 → 회차 → 저장 → 조회, 실패 갈래, 지운 기사 따르기를 실제 PostgreSQL 과 결선된 저장소 · 공개 조회로 본다.
 * <p>
 * 회차는 시험이 평가기와 고정 시계를 넣어 직접 만든다 — 작업 서버를 꺼 반복 작업이 시험의 회차와 겹치지 않는다. 기본 체계(버전 1, 처음 태그 기준 · 저장 기준
 * 0.5 / 0.1)를 쓰고, 테스트마다 기사 · 대기 · 평가 행과 태그 기준 · 저장 기준을 처음으로 되돌린다.
 * <p>
 * 지키는 규칙: evaluation-R14 · evaluation-R15 · evaluation-R18 · evaluation-R19 · evaluation-R20 · evaluation-R21
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class EvaluationFlowTests {

	private static final Instant T = Instant.parse("2026-11-02T00:00:00Z");

	/** 넣은 지 정착 시간(30분)이 지난 시각 */
	private static final Instant DUE = Instant.parse("2026-11-02T00:30:00Z");

	@Autowired
	PendingEvaluations pending;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	FeedLabels feedLabels;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	EvaluationQueueRepository queue;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ArticleEvaluations evaluations;

	@Autowired
	EvaluationAdministration administration;

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	JdbcClient jdbc;

	private final AtomicReference<Instant> now = new AtomicReference<>(DUE);

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		this.jdbc.sql("delete from evaluation_queue").update();
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
		this.jdbc.sql("delete from source_attributions where name = '기록일보'").update();
		this.jdbc.sql("delete from sources where preset_key = 'evaluation-flow'").update();
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void aStoredArticleIsEvaluatedAfterTheSettleDelayAndOnlyOnce() {
		long article = article("국회 본회의 통과", 1, 11);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ArticleStored(new ArticleId(article))));
		await().atMost(Duration.ofSeconds(10)).until(() -> queueRows() == 1);
		Instant eligible = eligibleAt(article);
		this.now.set(eligible.minusSeconds(1));
		round(new FakeArticleEvaluator()).run();
		long beforeDue = evaluationRows();
		this.now.set(eligible);
		round(new FakeArticleEvaluator()).run();

		assertThat(beforeDue).isZero();
		assertThat(evaluationRows()).isEqualTo(1);
		assertThat(this.jdbc.sql("select count(*) from article_tags").query(Long.class).single()).isPositive();
		assertThat(queueRows()).isZero();

		// 같은 이벤트를 다시 처리해도 평가는 한 번이다 — 대기 행은 다시 생기고 다음 회차가 지운다
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ArticleStored(new ArticleId(article))));
		await().atMost(Duration.ofSeconds(10)).until(() -> queueRows() == 1);
		this.now.set(eligibleAt(article));
		round(new FakeArticleEvaluator()).run();

		assertThat(evaluationRows()).isEqualTo(1);
		assertThat(queueRows()).isZero();
	}

	@Test
	void theInputCarriesTheFeedLabelsCategoriesAndNoBlankSummary() {
		long source = this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values ('evaluation-flow', false, 0, now())
				returning id
				""").query(Long.class).single();
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, '기록일보', now())")
			.param("source", source)
			.update();
		long feed = this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at) values (:source, 'all', false, 0, now())
				returning id
				""").param("source", source).query(Long.class).single();
		long article = article("국회 본회의 통과", source, feed, "   ", "정치", "국회");
		this.queue.enqueue(new ArticleId(article), T);
		Recording recording = new Recording(new FakeArticleEvaluator());

		try {
			round(recording).run();
		}
		finally {
			this.jdbc.sql("delete from feeds where id = :feed").param("feed", feed).update();
		}

		// 사라진 프리셋이라 이름은 기록된 출처 이름, 섹션은 없다. 공백뿐인 요약은 없는 요약이다
		assertThat(recording.inputs).containsExactly(new EvaluationInput("국회 본회의 통과", null,
				List.of(new FeedContext("기록일보", null, List.of("정치", "국회")))));
	}

	@Test
	void transientFailuresRetryUntilTheLimitWhileOthersInTheRoundAreEvaluated() {
		long transientArticle = enqueued("[fake:transient] 지연");
		long rejected = enqueued("[fake:article-rejected] 거부");
		long ordinary = enqueued("경제 지표 발표");

		round(new FakeArticleEvaluator()).run();

		assertThat(attemptsAndEligible(transientArticle)).isEqualTo(Map.of("attempts", 1, "eligible",
				Instant.parse("2026-11-02T00:31:00Z")));
		assertThat(failure(rejected)).isEqualTo("ARTICLE_REJECTED");
		assertThat(this.results.exists(new ArticleId(ordinary), this.directory.activeTagSet().id())).isTrue();

		for (String at : List.of("2026-11-02T00:31:00Z", "2026-11-02T00:33:00Z", "2026-11-02T00:37:00Z",
				"2026-11-02T00:45:00Z")) {
			this.now.set(Instant.parse(at));
			round(new FakeArticleEvaluator()).run();
		}

		assertThat(failure(transientArticle)).isEqualTo("RETRIES_EXHAUSTED");
		assertThat(this.administration.queueCounts()).isEqualTo(new EvaluationQueueCounts(0, 1, 1));
	}

	@Test
	void theRetryDelayCountsFromWhenTheFailureCameBack() {
		long article = enqueued("[fake:transient] 지연");
		// 평가에 10초가 걸린다 — 재시도 간격은 점유한 시각이 아니라 일시 실패를 받은 시각부터 잰다
		ArticleEvaluator slow = (input, tagSet) -> {
			this.now.set(this.now.get().plusSeconds(10));
			return new FakeArticleEvaluator().evaluate(input, tagSet);
		};

		round(slow).run();

		assertThat(attemptsAndEligible(article)).isEqualTo(Map.of("attempts", 1, "eligible",
				Instant.parse("2026-11-02T00:31:10Z")));
	}

	@Test
	void theEvaluatorIsCalledOutsideATransactionAndARoundTakesFiftyArticles() {
		for (int i = 0; i < 51; i++) {
			enqueued("기사 " + i);
		}
		Recording recording = new Recording(new FakeArticleEvaluator());
		// 오라클 실현성 — 같은 기록기를 트랜잭션 안에서 부르면 트랜잭션 있음으로 기록한다
		Recording probe = new Recording(new FakeArticleEvaluator());
		this.transaction.executeWithoutResult(status -> probe.evaluate(new EvaluationInput("확인", null, List.of()),
				this.directory.activeTagSet()));

		round(recording).run();

		assertThat(probe.inTransaction).containsExactly(true);
		assertThat(recording.inTransaction).hasSize(50).containsOnly(false);
		assertThat(evaluationRows()).isEqualTo(50);
		assertThat(this.jdbc.sql("select count(*) from evaluation_queue where lease_token is null")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void anUnavailableEvaluatorStopsTheRoundAndLeavesTheRestWaiting(CapturedOutput output) {
		long first = enqueued("경제 지표 발표");
		long unavailable = enqueued("[fake:evaluator-unavailable] 키 무효");
		long third = enqueued("날씨 맑음");

		round(new FakeArticleEvaluator()).run();

		assertThat(evaluatedArticles()).containsExactly(first);
		assertThat(untouched(unavailable)).isTrue();
		assertThat(untouched(third)).isTrue();
		assertThat(output).contains("평가기를 쓸 수 없어 회차를 멈췄다 — 기사 " + unavailable);
	}

	@Test
	void aResultThatDoesNotMatchTheTagSetStopsTheRound(CapturedOutput output) {
		long first = enqueued("경제 지표 발표");
		long defective = enqueued("결함 키 빠짐");
		long third = enqueued("날씨 맑음");

		round(new Defective("결함", false)).run();

		assertThat(evaluatedArticles()).containsExactly(first);
		assertThat(untouched(defective)).isTrue();
		assertThat(untouched(third)).isTrue();
		assertThat(output).contains("평가 결과가 체계와 맞지 않아 회차를 멈췄다 — 기사 " + defective + ": 주제 키가 체계와 다르다");
	}

	@Test
	void anUnexpectedExceptionStopsTheRound(CapturedOutput output) {
		long first = enqueued("경제 지표 발표");
		long defective = enqueued("결함 예외");
		long third = enqueued("날씨 맑음");

		round(new Defective("결함", true)).run();

		assertThat(evaluatedArticles()).containsExactly(first);
		assertThat(untouched(defective)).isTrue();
		assertThat(untouched(third)).isTrue();
		assertThat(output).contains("평가기가 예상 못한 예외를 던져 회차를 멈췄다 — 기사 " + defective);
	}

	@Test
	void withoutAnEvaluatorARoundLeavesTheQueueAlone() {
		long article = enqueued("경제 지표 발표");
		EvaluationRound none = round(new StaticListableBeanFactory().getBeanProvider(ArticleEvaluator.class));

		none.run();

		assertThat(none.evaluatorAvailable()).isFalse();
		assertThat(untouched(article)).isTrue();
		assertThat(evaluationRows()).isZero();

		// 대조 — 같은 행을 평가기 있는 회차는 평가한다
		EvaluationRound some = round(new FakeArticleEvaluator());
		some.run();

		assertThat(some.evaluatorAvailable()).isTrue();
		assertThat(evaluatedArticles()).containsExactly(article);
	}

	@Test
	void theEvaluationIsJudgedWithTheCurrentThresholdsWithoutRewritingIt() {
		long article = enqueued("국회 본회의 통과");
		round(new Literal()).run();

		Optional<ArticleEvaluationView> first = this.evaluations.find(new ArticleId(article));
		change("0.7", "0.1");
		Optional<ArticleEvaluationView> raisedTag = this.evaluations.find(new ArticleId(article));
		change("0.7", "0.65");
		Optional<ArticleEvaluationView> raisedStore = this.evaluations.find(new ArticleId(article));

		ArticleId id = new ArticleId(article);
		assertThat(first).hasValueSatisfying(view -> {
			assertThat(view.articleId()).isEqualTo(id);
			assertThat(view.topicStatus()).isEqualTo(TopicStatus.TAGGED);
			assertThat(view.topics()).containsExactly(new TagProbability("politics.legislature", 0.625));
			assertThat(view.topicCategories()).containsExactly("politics");
			assertThat(view.regions()).containsExactly(new TagProbability("domestic", 0.75));
			assertThat(view.format()).isEqualTo("straight");
			assertThat(view.importance()).isEqualTo(3.0);
		});
		assertThat(raisedTag).map(ArticleEvaluationView::topicStatus).contains(TopicStatus.LOW_CONFIDENCE);
		assertThat(raisedTag).map(ArticleEvaluationView::topics)
			.contains(List.of(new TagProbability("politics.legislature", 0.625)));
		assertThat(raisedStore).map(ArticleEvaluationView::topicStatus).contains(TopicStatus.UNCLASSIFIED);
		assertThat(raisedStore).map(ArticleEvaluationView::topicCategories).contains(List.of());
		// 기준을 바꿔도 저장된 태그는 그대로다
		assertThat(this.jdbc.sql("select tag_key, probability from article_tags order by tag_key").query().listOfRows())
			.containsExactly(Map.of("tag_key", "domestic", "probability", 0.75),
					Map.of("tag_key", "economy.finance", "probability", 0.375),
					Map.of("tag_key", "politics.legislature", "probability", 0.625));
		assertThat(this.evaluations.find(new ArticleId(article + 1_000))).isEmpty();
	}

	@Test
	void aQueuedArticleThatWasDeletedIsDroppedWithoutEvaluation(CapturedOutput output) {
		this.queue.enqueue(new ArticleId(999_999), T);

		round(new FakeArticleEvaluator()).run();

		assertThat(queueRows()).isZero();
		assertThat(evaluationRows()).isZero();
		assertThat(output).contains("평가 대기 기사가 지워져 대기에서 뺐다 — 기사 999999");
	}

	@Test
	void deletingASourceRemovesTheEvaluationsOfItsArticlesOnly() {
		long gone = enqueued("경제 지표 발표", 31, 311);
		long kept = enqueued("날씨 맑음", 32, 321);
		round(new FakeArticleEvaluator()).run();
		this.queue.enqueue(new ArticleId(gone), T);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new SourceDeleted(new SourceId(31))));
		await().atMost(Duration.ofSeconds(10)).until(() -> evaluatedArticles().equals(List.of(kept)));

		assertThat(this.jdbc.sql("select id from articles").query(Long.class).list()).containsExactly(kept);
		assertThat(this.jdbc.sql("select distinct article_id from article_tags").query(Long.class).list())
			.containsExactly(kept);
		assertThat(queueRows()).isZero();
	}

	@Test
	void aResultIsDiscardedWhenTheArticleIsRemovedDuringItsEvaluation() {
		long article = enqueued("경제 지표 발표");
		ArticleEventsListener listener = new ArticleEventsListener(this.queue, this.results);
		ArticleEvaluator removing = (input, tagSet) -> {
			this.transaction.executeWithoutResult(
					status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(article)))));
			return new FakeArticleEvaluator().evaluate(input, tagSet);
		};

		round(removing).run();

		assertThat(evaluationRows()).isZero();
		assertThat(queueRows()).isZero();
	}

	@Test
	void aRemovalWaitsForAnInFlightSaveAndThenRemovesItsResult() throws Exception {
		long article = enqueued("경제 지표 발표");
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		EvaluationResultRepository pausing = new Pausing(this.results, inserted, release);
		ArticleEventsListener listener = new ArticleEventsListener(this.queue, this.results);
		try {
			CompletableFuture<Void> saving = CompletableFuture.runAsync(() -> round(new FakeArticleEvaluator(), pausing).run());
			assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
			// 저장 트랜잭션이 대기 행을 지우고 평가 행을 넣은 채 커밋 전이다 — 삭제는 대기 행에서 기다린다
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> this.transaction
				.executeWithoutResult(status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(article))))));
			Thread.sleep(300);
			boolean waited = !removing.isDone();
			release.countDown();
			saving.get(10, TimeUnit.SECONDS);
			removing.get(10, TimeUnit.SECONDS);

			assertThat(waited).isTrue();
			assertThat(evaluationRows()).isZero();
			assertThat(this.jdbc.sql("select count(*) from article_tags").query(Long.class).single()).isZero();
		}
		finally {
			release.countDown();
		}
	}

	private void change(String tag, String store) {
		this.tagSetAdministration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal(tag), new BigDecimal(store),
				ChangeActor.OPERATOR);
	}

	private EvaluationRound round(ArticleEvaluator evaluator) {
		return round(evaluator, this.results);
	}

	private EvaluationRound round(ArticleEvaluator evaluator, EvaluationResultRepository results) {
		StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("evaluator", evaluator));
		return new EvaluationRound(beans.getBeanProvider(ArticleEvaluator.class), this.pending, this.directory,
				this.thresholds, this.articles, this.feedLabels, results, this.queue, this.transaction,
				(InstantSource) this.now::get);
	}

	private EvaluationRound round(org.springframework.beans.factory.ObjectProvider<ArticleEvaluator> evaluators) {
		return new EvaluationRound(evaluators, this.pending, this.directory, this.thresholds, this.articles,
				this.feedLabels, this.results, this.queue, this.transaction, (InstantSource) this.now::get);
	}

	/** 대기에 넣은 기사 — 넣은 시각 T, 소스 1 · 피드 11 */
	private long enqueued(String title) {
		return enqueued(title, 1, 11);
	}

	private long enqueued(String title, long source, long feed) {
		long article = article(title, source, feed);
		this.queue.enqueue(new ArticleId(article), T);
		return article;
	}

	private long article(String title, long source, long feed, @Nullable String summary, String... categories) {
		String url = "https://flow.example/" + UUID.randomUUID();
		long id = this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:title, 'UTF8')), :summary,
				    '2026-11-01T23:00:00Z', false, '2026-11-01T23:00:00Z')
				returning id
				""").param("url", url).param("title", title).param("summary", summary, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:article, :feed, :source, sha256(convert_to(:url, 'UTF8')), :categories, false, 'NEW',
				    '2026-11-01T23:00:00Z')
				""")
			.param("article", id)
			.param("feed", feed)
			.param("source", source)
			.param("url", url)
			.param("categories", categories)
			.update();
		return id;
	}

	private long article(String title, long source, long feed) {
		return article(title, source, feed, null);
	}

	private long queueRows() {
		return this.jdbc.sql("select count(*) from evaluation_queue").query(Long.class).single();
	}

	private long evaluationRows() {
		return this.jdbc.sql("select count(*) from article_evaluations").query(Long.class).single();
	}

	private List<Long> evaluatedArticles() {
		return this.jdbc.sql("select article_id from article_evaluations order by article_id").query(Long.class).list();
	}

	private Instant eligibleAt(long article) {
		return this.jdbc.sql("select eligible_at from evaluation_queue where article_id = :article")
			.param("article", article)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private Map<String, Object> attemptsAndEligible(long article) {
		return this.jdbc.sql("select attempts, eligible_at from evaluation_queue where article_id = :article")
			.param("article", article)
			.query((rs, row) -> Map.<String, Object>of("attempts", rs.getInt("attempts"), "eligible",
					rs.getObject("eligible_at", OffsetDateTime.class).toInstant()))
			.single();
	}

	private @Nullable String failure(long article) {
		return this.jdbc.sql("select failure from evaluation_queue where article_id = :article")
			.param("article", article)
			.query(String.class)
			.single();
	}

	/** 시도 0 · 점유 없음 · 실패 없음 · 평가 가능 시각 그대로(넣은 시각 + 30분) */
	private boolean untouched(long article) {
		return this.jdbc.sql("""
				select count(*) from evaluation_queue
				where article_id = :article and attempts = 0 and lease_token is null and failure is null
				    and eligible_at = :due
				""").param("article", article).param("due", DUE.atOffset(java.time.ZoneOffset.UTC))
			.query(Long.class)
			.single() == 1;
	}

	/** 받은 입력과 그때 트랜잭션이 있었는지를 기록한다 */
	private static final class Recording implements ArticleEvaluator {

		final List<EvaluationInput> inputs = new ArrayList<>();

		final List<Boolean> inTransaction = new ArrayList<>();

		private final ArticleEvaluator delegate;

		Recording(ArticleEvaluator delegate) {
			this.delegate = delegate;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
			this.inputs.add(input);
			this.inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
			return this.delegate.evaluate(input, tagSet);
		}

	}

	/** 제목에 표지가 있으면 결함을 낸다 — 주제 키 하나를 뺀 결과, 또는 예외 */
	private static final class Defective implements ArticleEvaluator {

		private final String marker;

		private final boolean throwing;

		Defective(String marker, boolean throwing) {
			this.marker = marker;
			this.throwing = throwing;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
			EvaluationResult result = new FakeArticleEvaluator().evaluate(input, tagSet);
			if (!input.title().contains(this.marker)) {
				return result;
			}
			if (this.throwing) {
				throw new IllegalStateException("시험 평가기의 결함");
			}
			Evaluation evaluation = (Evaluation) result;
			Map<String, Double> topics = new HashMap<>(evaluation.topics());
			topics.remove(topics.keySet().iterator().next());
			return new Evaluation(topics, evaluation.regions(), evaluation.formats(), evaluation.importance(),
					evaluation.evaluator(), evaluation.modelVersion(), evaluation.inputTokens(), evaluation.rawResponse());
		}

	}

	/** 기본 체계에 리터럴 확률을 준다 — 주제 국회 0.625 · 금융 0.375, 지역 국내 0.75, 형식 스트레이트 0.75, 중요도 {2:0.5, 4:0.5} */
	private static final class Literal implements ArticleEvaluator {

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
			Map<String, Double> topics = new HashMap<>();
			tagSet.topics().forEach(category -> category.subtopics().forEach(tag -> topics.put(tag.key(), 0.0)));
			topics.put("politics.legislature", 0.625);
			topics.put("economy.finance", 0.375);
			Map<String, Double> regions = new HashMap<>();
			tagSet.regions().stream().map(AskedTag::key).forEach(key -> regions.put(key, 0.0));
			regions.put("domestic", 0.75);
			Map<String, Double> formats = new HashMap<>();
			tagSet.format().options().stream().map(FormatOption::key).forEach(key -> formats.put(key, 0.0));
			formats.put("straight", 0.75);
			Map<Integer, Double> importance = new HashMap<>();
			tagSet.importance().levels().stream().map(ImportanceLevel::level).forEach(level -> importance.put(level, 0.0));
			importance.put(2, 0.5);
			importance.put(4, 0.5);
			return new Evaluation(topics, regions, formats, importance, "literal", "literal-1", 1, "{}");
		}

	}

	/** 평가 행을 넣은 뒤 트랜잭션 안에서 신호를 보내고 풀릴 때까지 기다린다 — 저장 트랜잭션을 커밋 직전에 세운다 */
	private static final class Pausing implements EvaluationResultRepository {

		private final EvaluationResultRepository delegate;

		private final CountDownLatch inserted;

		private final CountDownLatch release;

		Pausing(EvaluationResultRepository delegate, CountDownLatch inserted, CountDownLatch release) {
			this.delegate = delegate;
			this.inserted = inserted;
			this.release = release;
		}

		@Override
		public boolean exists(ArticleId articleId, net.yozmnews.evaluation.TagSetId tagSetId) {
			return this.delegate.exists(articleId, tagSetId);
		}

		@Override
		public void insert(EvaluationRecord record) {
			this.delegate.insert(record);
			this.inserted.countDown();
			try {
				this.release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public Optional<StoredEvaluation> findLatest(ArticleId articleId) {
			return this.delegate.findLatest(articleId);
		}

		@Override
		public void deleteByArticles(java.util.Collection<ArticleId> articleIds) {
			this.delegate.deleteByArticles(articleIds);
		}

	}

}
