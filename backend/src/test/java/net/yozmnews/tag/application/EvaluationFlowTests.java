package net.yozmnews.tag.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagAnswers;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TaggingJudgement;
import net.yozmnews.evaluation.TaggingQuery;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationAdministration;
import net.yozmnews.tag.EvaluationQueueCounts;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.result.EvaluationRecord;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.StoredEvaluation;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 평가 흐름 — 대기 → 회차 → 저장 → 조회, 실패 갈래, 지운 기사 따르기를 실제 PostgreSQL 과 결선된 저장소 · 공개 조회로 본다.
 * <p>
 * 회차는 시험이 태그 판정 대역({@link ScriptedTagging})과 고정 시계를 넣어 직접 만든다 — 작업 서버를 꺼 반복 작업이 시험의 회차와 겹치지
 * 않는다. 판정의 결과(판정 · 실패 갈래 · 기사 없음 · 예외)는 대역의 대본이고, 기사 읽기 · 입력 만들기 · 결과 대조 · 판정 원본 기록은
 * 관문({@code evaluation})의 시험이 본다. 기본 체계(버전 1, 처음 태그 기준 · 저장 기준 0.5 / 0.1)를 쓰고, 테스트마다 기사 · 대기 · 평가
 * 행과 태그 기준 · 저장 기준을 처음으로 되돌린다.
 * <p>
 * 지키는 규칙: tag-R14 · tag-R15 · tag-R18 · tag-R19 · tag-R20 · tag-R21. 회차 중의 활성 전환(tag-R19 의 활성 확인)은 시험이 저장소
 * 수단으로 버전 2 를 켜서 만든다
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
	ArticleTagging tagging;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	EvaluationQueueRepository queue;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	TagSetVersioning versioning;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ArticleEvaluations evaluations;

	@Autowired
	EvaluationAdministration administration;

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	ArticleEventsListener articleEvents;

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
		this.jdbc.sql("delete from article_tag_judgements").update();
		this.jdbc.sql("delete from evaluation_queue").update();
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void aResultOfAVersionRetiredDuringTheRoundIsKeptAndTheArticleStaysQueuedForTheActiveVersion(CapturedOutput output) {
		long first = enqueued("국회 본회의 통과");
		long second = enqueued("경제 지표 발표");
		TagSetId retired = this.directory.activeTagSet().id();
		AtomicInteger calls = new AtomicInteger();
		ScriptedTagging switching = scripted().judging(query -> {
			if (calls.getAndIncrement() == 0) {
				switchToASecondVersion();
			}
			return ScriptedTagging.judged(query.questions());
		});

		round(switching).run();

		// 은퇴한 버전 1 의 평가는 저장하고, 대기 행은 남겨 점유만 푼다. 뒤 기사는 평가하지 않고 점유를 풀어 회차를 멈춘다
		assertThat(calls.get()).isEqualTo(1);
		assertThat(this.results.exists(new ArticleId(first), retired)).isTrue();
		assertThat(waitingWithoutLease(first)).isTrue();
		assertThat(untouched(second)).isTrue();
		assertThat(output).contains("활성 태그 체계가 바뀌어 회차를 멈췄다 — 기사 " + first);

		round(scripted()).run();

		TagSetId active = this.directory.activeTagSet().id();
		assertThat(active).isNotEqualTo(retired);
		assertThat(this.evaluations.find(new ArticleId(first)).orElseThrow().tagSetId()).isEqualTo(active);
		assertThat(this.evaluations.find(new ArticleId(second)).orElseThrow().tagSetId()).isEqualTo(active);
		assertThat(queueRows()).isZero();
	}

	@Test
	void aRejectionFromAVersionRetiredDuringTheRoundDoesNotFailTheArticle() {
		long article = enqueued("거부");
		ScriptedTagging switching = scripted().judging(query -> {
			switchToASecondVersion();
			return failed(EvaluatorFailureKind.ARTICLE_REJECTED);
		});

		round(switching).run();

		// 실패로 남기지 않고 점유만 푼다 — 시도 수도 그대로다
		assertThat(waitingWithoutLease(article)).isTrue();
		assertThat(attemptsAndEligible(article)).containsEntry("attempts", 0);
	}

	@Test
	void aRejectionWithoutAnActivationFailsTheArticle() {
		long article = enqueued("거부");

		round(failing(Map.of(article, EvaluatorFailureKind.ARTICLE_REJECTED))).run();

		assertThat(failure(article)).isEqualTo("ARTICLE_REJECTED");
	}

	@Test
	void anArticleAlreadyEvaluatedWithAVersionRetiredBeforeTheCheckStaysQueued() {
		long article = enqueued("국회 본회의 통과");
		round(scripted()).run();
		this.queue.enqueue(new ArticleId(article), T);
		ScriptedTagging counting = scripted();
		// 회차가 활성 버전을 읽은 뒤, 그 버전의 평가가 있는지 보기 전에 활성이 바뀐다
		EvaluationResultRepository switching = new Delegating(this.results) {
			@Override
			public boolean exists(ArticleId articleId, net.yozmnews.tag.TagSetId tagSetId) {
				switchToASecondVersion();
				return super.exists(articleId, tagSetId);
			}
		};

		round(counting, switching).run();

		assertThat(counting.calls()).isEmpty();
		assertThat(waitingWithoutLease(article)).isTrue();
	}

	@Test
	void anArticleAlreadyEvaluatedWithTheActiveVersionLeavesTheQueue() {
		long article = enqueued("국회 본회의 통과");
		round(scripted()).run();
		this.queue.enqueue(new ArticleId(article), T);

		round(scripted()).run();

		assertThat(queueRows()).isZero();
	}

	@Test
	void aResultWriteWaitsWhileAnActivationHoldsTheTagSetTable() throws Exception {
		enqueued("국회 본회의 통과");
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<CompletableFuture<Void>> holder = new AtomicReference<>();
		// 판정이 끝나기 전에 다른 연결이 활성 전환의 잠금을 쥔다 — 결과를 쓰는 트랜잭션이 활성을 본 뒤 전환이 끼어들 틈을 막는지 본다
		ScriptedTagging holding = scripted().judging(query -> {
			holder.set(CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
				this.jdbc.sql("lock table tag_sets in share row exclusive mode").update();
				locked.countDown();
				awaitQuietly(release);
			})));
			awaitQuietly(locked);
			return ScriptedTagging.judged(query.questions());
		});
		try {
			CompletableFuture<Void> running = CompletableFuture.runAsync(() -> round(holding).run());
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(300);
			boolean waited = !running.isDone();
			release.countDown();
			running.get(10, TimeUnit.SECONDS);
			holder.get().get(10, TimeUnit.SECONDS);

			assertThat(waited).isTrue();
			assertThat(queueRows()).isZero();
			assertThat(evaluationRows()).isEqualTo(1);
		}
		finally {
			release.countDown();
		}
	}

	@Test
	void aStoredArticleIsEvaluatedAfterTheSettleDelayAndOnlyOnce() {
		long article = article("국회 본회의 통과", 1, 11);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ArticleStored(new ArticleId(article))));
		await().atMost(Duration.ofSeconds(10)).until(() -> queueRows() == 1);
		Instant eligible = QueueRows.eligibleAt(this.jdbc, article);
		this.now.set(eligible.minusSeconds(1));
		round(scripted()).run();
		long beforeDue = evaluationRows();
		this.now.set(eligible);
		round(scripted()).run();

		assertThat(beforeDue).isZero();
		assertThat(evaluationRows()).isEqualTo(1);
		assertThat(this.jdbc.sql("select count(*) from article_tags").query(Long.class).single()).isPositive();
		assertThat(queueRows()).isZero();

		// 같은 이벤트를 다시 처리해도 평가는 한 번이다 — 대기 행은 다시 생기고 다음 회차가 지운다
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ArticleStored(new ArticleId(article))));
		await().atMost(Duration.ofSeconds(10)).until(() -> queueRows() == 1);
		this.now.set(QueueRows.eligibleAt(this.jdbc, article));
		ScriptedTagging again = scripted();
		round(again).run();

		assertThat(again.calls()).isEmpty();
		assertThat(evaluationRows()).isEqualTo(1);
		assertThat(queueRows()).isZero();
	}

	@Test
	void transientFailuresRetryUntilTheLimitWhileOthersInTheRoundAreEvaluated() {
		long transientArticle = enqueued("지연");
		long rejected = enqueued("거부");
		long ordinary = enqueued("경제 지표 발표");
		ScriptedTagging failing = failing(Map.of(transientArticle, EvaluatorFailureKind.TRANSIENT, rejected,
				EvaluatorFailureKind.ARTICLE_REJECTED));

		round(failing).run();

		assertThat(attemptsAndEligible(transientArticle)).isEqualTo(Map.of("attempts", 1, "eligible",
				Instant.parse("2026-11-02T00:31:00Z")));
		assertThat(failure(rejected)).isEqualTo("ARTICLE_REJECTED");
		assertThat(this.results.exists(new ArticleId(ordinary), this.directory.activeTagSet().id())).isTrue();

		for (String at : List.of("2026-11-02T00:31:00Z", "2026-11-02T00:33:00Z", "2026-11-02T00:37:00Z",
				"2026-11-02T00:45:00Z")) {
			this.now.set(Instant.parse(at));
			round(failing).run();
		}

		assertThat(failure(transientArticle)).isEqualTo("RETRIES_EXHAUSTED");
		assertThat(this.administration.queueCounts()).isEqualTo(new EvaluationQueueCounts(0, 1, 1));
	}

	@Test
	void theRetryDelayCountsFromWhenTheFailureCameBack() {
		long article = enqueued("지연");
		// 판정에 10초가 걸린다 — 재시도 간격은 점유한 시각이 아니라 일시 실패를 받은 시각부터 잰다
		ScriptedTagging slow = scripted().judging(query -> {
			this.now.set(this.now.get().plusSeconds(10));
			return failed(EvaluatorFailureKind.TRANSIENT);
		});

		round(slow).run();

		assertThat(attemptsAndEligible(article)).isEqualTo(Map.of("attempts", 1, "eligible",
				Instant.parse("2026-11-02T00:31:10Z")));
	}

	@Test
	void theJudgementIsCalledOutsideATransactionWithTheActiveQuestionsAndARoundTakesFiftyArticles() {
		for (int i = 0; i < 51; i++) {
			enqueued("기사 " + i);
		}
		ScriptedTagging recording = scripted();
		TagQuestions active = this.directory.activeTagSet().questions();
		// 오라클 실현성 — 같은 대역을 트랜잭션 안에서 부르면 트랜잭션 있음으로 기록한다
		ScriptedTagging probe = scripted();
		this.transaction.executeWithoutResult(status -> probe.judge(new TaggingQuery(new ArticleId(1), active)));

		round(recording).run();

		assertThat(probe.calls()).extracting(ScriptedTagging.Call::inTransaction).containsExactly(true);
		assertThat(recording.calls()).hasSize(50)
			.extracting(ScriptedTagging.Call::inTransaction)
			.containsOnly(false);
		assertThat(recording.calls()).extracting(call -> call.query().questions()).containsOnly(active);
		assertThat(evaluationRows()).isEqualTo(50);
		assertThat(this.jdbc.sql("select count(*) from evaluation_queue where lease_token is null")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void anUnavailableEvaluatorStopsTheRoundAndLeavesTheRestWaiting(CapturedOutput output) {
		long first = enqueued("경제 지표 발표");
		long unavailable = enqueued("키 무효");
		long third = enqueued("날씨 맑음");

		round(failing(Map.of(unavailable, EvaluatorFailureKind.EVALUATOR_UNAVAILABLE))).run();

		assertThat(evaluatedArticles()).containsExactly(first);
		assertThat(untouched(unavailable)).isTrue();
		assertThat(untouched(third)).isTrue();
		assertThat(output).contains("평가기를 쓸 수 없어 회차를 멈췄다 — 기사 " + unavailable);
	}

	@Test
	void anExceptionOfTheJudgementStopsTheRound(CapturedOutput output) {
		long first = enqueued("경제 지표 발표");
		long defective = enqueued("결함 예외");
		long third = enqueued("날씨 맑음");
		// 태그 판정의 예외 — 결과가 질문 묶음과 어긋남(관문이 대조한다) · 평가기의 예상 못한 예외
		ScriptedTagging throwing = scripted().judging(query -> {
			if (query.articleId().value() == defective) {
				throw new IllegalStateException("시험 판정의 결함");
			}
			return ScriptedTagging.judged(query.questions());
		});

		round(throwing).run();

		assertThat(evaluatedArticles()).containsExactly(first);
		assertThat(untouched(defective)).isTrue();
		assertThat(untouched(third)).isTrue();
		assertThat(output).contains("태그 판정이 실패해 회차를 멈췄다 — 기사 " + defective).contains("시험 판정의 결함");
	}

	@Test
	void theEvaluationIsJudgedWithTheCurrentThresholdsWithoutRewritingIt() {
		long article = enqueued("국회 본회의 통과");
		round(scripted().judging(query -> ScriptedTagging.judged(literal(query.questions())))).run();

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

		round(scripted().judging(query -> new TaggingJudgement.ArticleMissing())).run();

		assertThat(queueRows()).isZero();
		assertThat(evaluationRows()).isZero();
		assertThat(output).contains("평가 대기 기사가 지워져 대기에서 뺐다 — 기사 999999");
	}

	@Test
	void aPublishedRemovalRemovesTheEvaluationsOfTheRemovedArticlesOnly() {
		long gone = enqueued("경제 지표 발표");
		long kept = enqueued("날씨 맑음");
		round(scripted()).run();
		this.queue.enqueue(new ArticleId(gone), T);
		// 오라클 실현성 — 지우기 전에는 두 기사 모두 평가 · 태그 행이 있고 지울 기사가 다시 대기에 있다
		assertThat(evaluatedArticles()).containsExactly(gone, kept);
		assertThat(this.jdbc.sql("select distinct article_id from article_tags order by article_id")
			.query(Long.class)
			.list()).containsExactly(gone, kept);
		assertThat(queueRows()).isEqualTo(1);

		this.transaction.executeWithoutResult(
				status -> this.events.publishEvent(new ArticlesRemoved(List.of(new ArticleId(gone)))));
		await().atMost(Duration.ofSeconds(10)).until(() -> evaluatedArticles().equals(List.of(kept)));

		assertThat(this.jdbc.sql("select distinct article_id from article_tags").query(Long.class).list())
			.containsExactly(kept);
		assertThat(queueRows()).isZero();
	}

	@Test
	void aResultIsDiscardedWhenTheArticleIsRemovedDuringItsEvaluation() {
		long article = enqueued("경제 지표 발표");
		ScriptedTagging removing = scripted().judging(query -> {
			this.transaction.executeWithoutResult(
					status -> listener().on(new ArticlesRemoved(List.of(new ArticleId(article)))));
			return ScriptedTagging.judged(query.questions());
		});

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
		ArticleEventsListener listener = listener();
		try {
			CompletableFuture<Void> saving = CompletableFuture.runAsync(() -> round(scripted(), pausing).run());
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

	/** 대본 없는 대역 — 판정은 {@link ScriptedTagging#judged(TagQuestions)}, 원본 조회는 결선된 운영 구현 */
	private ScriptedTagging scripted() {
		return new ScriptedTagging(this.tagging);
	}

	/** 넘긴 기사는 그 갈래로 실패하고 나머지는 판정한다 */
	private ScriptedTagging failing(Map<Long, EvaluatorFailureKind> failures) {
		return scripted().judging(query -> {
			@Nullable EvaluatorFailureKind kind = failures.get(query.articleId().value());
			return (kind != null) ? failed(kind) : ScriptedTagging.judged(query.questions());
		});
	}

	private static TaggingJudgement failed(EvaluatorFailureKind kind) {
		return new TaggingJudgement.Failed(kind, "시험이 정한 실패 — " + kind);
	}

	/**
	 * 질문 묶음에 리터럴 확률을 준다 — 주제 국회 0.625 · 금융 0.375, 지역 국내 0.75, 형식 스트레이트 0.75, 중요도 {2:0.5, 4:0.5}, 나머지는
	 * 0.0
	 */
	private static TagAnswers literal(TagQuestions questions) {
		TagAnswers base = ScriptedTagging.answers(questions, 0.0,
				Map.of("politics.legislature", 0.625, "economy.finance", 0.375), Map.of("domestic", 0.75));
		Map<String, Double> formats = new HashMap<>();
		questions.format().options().stream().map(FormatOption::key).forEach(key -> formats.put(key, 0.0));
		formats.put("straight", 0.75);
		Map<Integer, Double> importance = new HashMap<>();
		questions.importance()
			.levels()
			.stream()
			.map(ImportanceLevel::level)
			.forEach(level -> importance.put(level, 0.0));
		importance.put(2, 0.5);
		importance.put(4, 0.5);
		return new TagAnswers(base.topics(), base.regions(), formats, importance);
	}

	/** 결선된 지운 기사 리스너의 대상 객체 — 비동기 · 새 트랜잭션 프록시를 거치지 않고 시험의 트랜잭션에서 바로 부른다 */
	private ArticleEventsListener listener() {
		return AopTestUtils.getUltimateTargetObject(this.articleEvents);
	}

	/** 시험의 시계로 만든 회차에 태그 판정을 넘겨 한 번 돈다 */
	private Runnable round(ArticleTagging tagging) {
		return round(tagging, this.results);
	}

	private Runnable round(ArticleTagging tagging, EvaluationResultRepository results) {
		InstantSource clock = this.now::get;
		EvaluationRound round = new EvaluationRound(this.pending, this.directory, this.thresholds, tagging, results,
				this.queue, this.tagSets, this.transaction, clock);
		return round::run;
	}

	/** 기본 체계에서 미국 설명 하나를 바꾼 버전 2 를 켠다 — 저장소 수단만 쓴다(대조 · 재평가 없음) */
	private void switchToASecondVersion() {
		String text = this.versioning.versionText(this.directory.activeTagSet().id()).orElseThrow()
			.replace("description: \"미국\"", "description: \"미국 · 미국령\"");
		TagSetVersions.activate(text, this.tagSets, this.thresholds, this.transaction, this.now.get());
	}

	/** 실패 없이 대기 중이고 점유가 없다 */
	private boolean waitingWithoutLease(long article) {
		return this.jdbc.sql("""
				select count(*) from evaluation_queue
				where article_id = :article and lease_token is null and failure is null
				""").param("article", article).query(Long.class).single() == 1;
	}

	/** 대기에 넣은 기사 — 넣은 시각 T, 소스 1 · 피드 11 */
	private long enqueued(String title) {
		long article = article(title, 1, 11);
		this.queue.enqueue(new ArticleId(article), T);
		return article;
	}

	/** 기사 하나와 그 엔트리 — 요약 · 카테고리 없음 */
	private long article(String title, long source, long feed) {
		String url = "https://flow.example/" + UUID.randomUUID();
		long id = this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:title, 'UTF8')), null,
				    '2026-11-01T23:00:00Z', false, '2026-11-01T23:00:00Z')
				returning id
				""").param("url", url).param("title", title)
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
			.param("categories", new String[0])
			.update();
		return id;
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

	/** 평가 행을 넣은 뒤 트랜잭션 안에서 신호를 보내고 풀릴 때까지 기다린다 — 저장 트랜잭션을 커밋 직전에 세운다 */
	private static final class Pausing extends Delegating {

		private final CountDownLatch inserted;

		private final CountDownLatch release;

		Pausing(EvaluationResultRepository delegate, CountDownLatch inserted, CountDownLatch release) {
			super(delegate);
			this.inserted = inserted;
			this.release = release;
		}

		@Override
		public void insert(EvaluationRecord record) {
			super.insert(record);
			this.inserted.countDown();
			try {
				this.release.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

	}

	/** 결과 저장소에 시험의 끼어들기를 얹는 바탕 — 그대로 넘긴다 */
	private static class Delegating implements EvaluationResultRepository {

		private final EvaluationResultRepository delegate;

		Delegating(EvaluationResultRepository delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean exists(ArticleId articleId, net.yozmnews.tag.TagSetId tagSetId) {
			return this.delegate.exists(articleId, tagSetId);
		}

		@Override
		public void insert(EvaluationRecord record) {
			this.delegate.insert(record);
		}

		@Override
		public Optional<StoredEvaluation> findLatest(ArticleId articleId) {
			return this.delegate.findLatest(articleId);
		}

		@Override
		public Map<ArticleId, Double> findLatestImportances(java.util.Collection<ArticleId> articleIds) {
			return this.delegate.findLatestImportances(articleIds);
		}

		@Override
		public void streamVersionPeriod(net.yozmnews.tag.TagSetId version, Instant from, Instant to,
				java.util.function.Consumer<net.yozmnews.tag.domain.result.SampleRow> sink) {
			this.delegate.streamVersionPeriod(version, from, to, sink);
		}

		@Override
		public void streamFirstEvaluatedIn(Instant from, Instant to, java.util.function.Consumer<StoredEvaluation> sink) {
			this.delegate.streamFirstEvaluatedIn(from, to, sink);
		}

		@Override
		public void scanNewestFirst(net.yozmnews.tag.TagSetId version, Instant from, Instant to,
				net.yozmnews.tag.@Nullable EvaluationCursor after,
				net.yozmnews.tag.domain.review.@Nullable StoredKeys required, @Nullable String formatKey,
				java.util.function.Predicate<net.yozmnews.tag.domain.result.ListedEvaluation> sink) {
			this.delegate.scanNewestFirst(version, from, to, after, required, formatKey, sink);
		}

		@Override
		public void streamIds(net.yozmnews.tag.TagSetId version, Instant from, Instant to,
				java.util.function.LongConsumer sink) {
			this.delegate.streamIds(version, from, to, sink);
		}

		@Override
		public List<net.yozmnews.tag.domain.result.ListedEvaluation> findListed(
				java.util.Collection<net.yozmnews.tag.EvaluationId> ids) {
			return this.delegate.findListed(ids);
		}

		@Override
		public Optional<net.yozmnews.tag.domain.result.StoredEvaluationDetail> findDetail(
				net.yozmnews.tag.EvaluationId id) {
			return this.delegate.findDetail(id);
		}

		@Override
		public List<net.yozmnews.tag.domain.result.BackfillTarget> lockForBackfill(Instant from, Instant to,
				long afterId, int limit) {
			return this.delegate.lockForBackfill(from, to, afterId, limit);
		}

		@Override
		public int addMissingTags(long evaluationId, ArticleId articleId,
				List<net.yozmnews.tag.domain.result.StoredTag> tags) {
			return this.delegate.addMissingTags(evaluationId, articleId, tags);
		}

		@Override
		public Map<net.yozmnews.tag.TagSetId, Long> countByVersion(Instant from, Instant to) {
			return this.delegate.countByVersion(from, to);
		}

		@Override
		public void deleteByArticles(java.util.Collection<ArticleId> articleIds) {
			this.delegate.deleteByArticles(articleIds);
		}

	}

}
