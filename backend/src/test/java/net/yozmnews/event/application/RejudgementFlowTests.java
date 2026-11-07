package net.yozmnews.event.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.DecidedBy;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.RejudgeTarget;
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
 * 재판정 — 기준이 바뀌면 다른 기준으로 판정된 배정을 저장된 점수 · 확률로 다시 판정해 옮기거나 떼어 내거나 그대로 둔다. 사건 소속 판정을
 * 부르지 않는다. 배정은 배정 회차로 만들고(기준 위 0.80 · 아래 0.55 · 저장 0.40 · 확률 0.50 · 창 72시간, 00:00), 재판정은 바꾼 기준으로
 * 01:00 에 돈다.
 * <p>
 * 사건 E 는 기사 a([1,0])가 연다. 기사 x([4,3], 핵심 말 없음)의 E 점수는 0.7 × 0.8 = 0.56(띠)이다. 벡터는 반 정밀도로 정확한 값이다.
 * <p>
 * 지키는 규칙: event-R18
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class RejudgementFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-06T01:00:00Z");

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

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void aLowerUpperMovesABandArticleByTheFormulaAndDropsItsEmptiedEvent() {
		long a = assigned("[1,0]", null);
		long event = eventOf(a);
		long x = assigned("[4,3]", 0.3);
		long opened = eventOf(x);
		assertThat(opened).isNotEqualTo(event);

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.55, 0.50, 0.40, 0.50));

		assertThat(eventOf(x)).isEqualTo(event);
		// x 는 판정 때 연 사건에서 옮겨졌다 — 연 사건 표지가 거짓이 된다
		assertThat(assignment(x)).containsEntry("decided_by", "FORMULA")
			.containsEntry("assigned", "2026-11-06T01:00:00Z")
			.containsEntry("opened", false);
		assertThat((Double) assignment(x).get("score")).isCloseTo(0.56, within(1e-3));
		// ([1,0] + [4,3]) / 2
		assertThat(eventRow(event)).containsEntry("centroid", "[2.5,1.5]").containsEntry("article_count", 2);
		assertThat(count("select count(*) from events where id = " + opened)).isZero();
		// a 는 사건을 연 기사라 E 에 남고 다시 적힌다(정한 것 · 기준 · 배정 시각)
		assertThat(eventOf(a)).isEqualTo(event);
		assertThat(assignment(a)).containsEntry("decided_by", "FORMULA")
			.containsEntry("assigned", "2026-11-06T01:00:00Z")
			.containsEntry("opened", true);
		assertThat(count("select count(distinct criteria_id) from event_articles")).isEqualTo(1);
		assertThat(criteriaOf(a)).isNotEqualTo(firstCriteria());
	}

	@Test
	void aHigherProbabilitySplitsAnEvaluatorAttachmentIntoANewEvent() {
		long a = assigned("[1,0]", null);
		long event = eventOf(a);
		long x = assigned("[4,3]", 0.7);
		assertThat(eventOf(x)).isEqualTo(event);
		assertThat(eventRow(event)).containsEntry("centroid", "[2.5,1.5]").containsEntry("article_count", 2);
		assertThat(assignment(x)).containsEntry("opened", false);

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.80));

		long split = eventOf(x);
		assertThat(split).isNotEqualTo(event);
		// 떼어 낸 새 사건은 그 기사가 연 사건이다
		assertThat(assignment(x)).containsEntry("decided_by", "EVALUATOR").containsEntry("opened", true);
		assertThat(eventRow(split)).containsEntry("centroid", "[4,3]")
			.containsEntry("article_count", 1)
			.containsEntry("model_version", "model-a");
		// ([2.5,1.5] × 2 − [4,3]) / 1 = [1,0] — a 는 사건을 연 기사라 떨어져 나가지 않는다
		assertThat(eventRow(event)).containsEntry("centroid", "[1,0]").containsEntry("article_count", 1);
		// a 를 재판정할 때 E 에는 x 가 붙어 있었다 — 연 기사는 떼어 내지 않고 다시 적는다
		assertThat(eventOf(a)).isEqualTo(event);
		assertThat(assignment(a)).containsEntry("assigned", "2026-11-06T01:00:00Z");
	}

	@Test
	void aMoveToAnEventThatIsNoLongerOpenKeepsTheAssignment() {
		long a = assigned("[1,0]", null);
		long event = eventOf(a);
		long x = assigned("[4,3]", 0.3);
		long opened = eventOf(x);
		close(event);

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.55, 0.50, 0.40, 0.50));

		assertThat(eventOf(x)).isEqualTo(opened);
		assertThat(assignment(x)).containsEntry("decided_by", "EVALUATOR").containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(criteriaOf(x)).isNotEqualTo(firstCriteria());
		assertThat(eventRow(event)).containsEntry("article_count", 1);
		assertThat(eventRow(opened)).containsEntry("article_count", 1);
	}

	@Test
	void aBandCandidateWithoutAProbabilityKeepsTheAssignment() {
		long a = assigned("[1,0]", null, "금리");
		long event = eventOf(a);
		// 0.56 + 0.3 × 1 = 0.86 — 산식으로 붙고 확률은 없다
		long b = assigned("[4,3]", null, "금리");
		assertThat(eventOf(b)).isEqualTo(event);

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.90, 0.55, 0.40, 0.50));

		assertThat(eventOf(b)).isEqualTo(event);
		assertThat(assignment(b)).containsEntry("decided_by", "FORMULA").containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(criteriaOf(b)).isEqualTo(criteriaOf(a)).isNotEqualTo(firstCriteria());
		assertThat(eventRow(event)).containsEntry("article_count", 2);
	}

	@Test
	void theSameCriteriaRejudgeNothing() {
		long a = assigned("[1,0]", null);
		long x = assigned("[4,3]", 0.3);

		rejudge(CRITERIA);

		assertThat(assignment(a)).containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(assignment(x)).containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(criteriaOf(a)).isEqualTo(firstCriteria());
		assertThat(criteriaOf(x)).isEqualTo(firstCriteria());
	}

	@Test
	void aMoveWithoutTheRepresentationKeepsTheAssignmentAndIsNotRejudgedAgain() {
		long a = assigned("[1,0]", null);
		long x = assigned("[4,3]", 0.3);
		long opened = eventOf(x);
		this.jdbc.sql("delete from article_match_features where article_id = :article").param("article", x).update();
		AssignmentCriteria lowered = new AssignmentCriteria(Duration.ofHours(72), 0.55, 0.50, 0.40, 0.50);

		rejudge(lowered);
		long criteria = criteriaOf(x);
		rejudge(lowered, Instant.parse("2026-11-06T02:00:00Z"));

		assertThat(eventOf(x)).isEqualTo(opened);
		assertThat(assignment(x)).containsEntry("decided_by", "EVALUATOR").containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(criteriaOf(x)).isEqualTo(criteria).isNotEqualTo(firstCriteria());
		assertThat(eventRow(eventOf(a))).containsEntry("article_count", 1);
	}

	@Test
	void aRemovalWaitsForARejudgementAndThenTakesTheArticleOut() throws Exception {
		long a = assigned("[1,0]", null);
		long event = eventOf(a);
		long x = assigned("[4,3]", 0.7);
		CountDownLatch moved = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		EventArticleRepository pausing = new ForwardingEventArticleRepository(this.assignments) {

			@Override
			public void reassign(ArticleId articleId, long eventId, @Nullable Double score, DecidedBy by,
					boolean opened, long criteriaId, Instant now) {
				super.reassign(articleId, eventId, score, by, opened, criteriaId, now);
				if (articleId.value() == x) {
					moved.countDown();
					awaitQuietly(resume);
				}
			}

		};
		RejudgementRound round = new RejudgementRound(pausing, this.events, this.features, this.criteriaHistory,
				new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.80), this.shrinks, this.transaction,
				(InstantSource) () -> LATER);
		try {
			CompletableFuture<Void> rejudging = CompletableFuture.runAsync(round::run);
			assertThat(moved.await(10, TimeUnit.SECONDS)).isTrue();
			// 재판정 트랜잭션이 x 의 배정 행을 잡고 새 사건으로 옮긴 채 커밋 전이다 — 지우기는 배정 행에서 기다린다
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> remove(x));
			Thread.sleep(500);
			assertThat(removing).as("재판정이 배정 행을 잡은 동안 지우기가 기다린다").isNotDone();
			resume.countDown();
			rejudging.get(10, TimeUnit.SECONDS);
			removing.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(this.assignments.exists(new ArticleId(x))).isFalse();
		assertThat(this.jdbc.sql("select id from events").query(Long.class).list()).containsExactly(event);
		assertThat(eventRow(event)).containsEntry("centroid", "[1,0]").containsEntry("article_count", 1);
	}

	@Test
	void anArticleThatOpenedAMergedEventStaysInTheSurvivor() {
		long y = assigned("[1,0]", null);
		long survivor = eventOf(y);
		// cos([3,4], [1,0]) = 0.6 → 0.7 × 0.6 = 0.42 — 저장 기준 이상 · 아래 기준 미만이라 a 는 새 사건 X 를 열고 Y 는 저장 후보로 남는다
		long a = assigned("[3,4]", null);
		long merged = eventOf(a);
		assertThat(merged).isNotEqualTo(survivor);
		mergeInto(merged, survivor, a);

		rejudge(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50));

		// a 는 X 를 연 기사다 — 저장 후보에 Y 가 있어도 붙었던 기사로 읽지 않는다
		assertThat(eventOf(a)).isEqualTo(survivor);
		assertThat(assignment(a)).containsEntry("opened", true).containsEntry("assigned", "2026-11-06T01:00:00Z");
		assertThat(eventRow(survivor)).containsEntry("article_count", 2);
		assertThat(count("select count(*) from events where status = 'OPEN'")).isEqualTo(1);
	}

	@Test
	void anArticleAttachedToTheSurvivorIsSplitByTheSameCriteria() {
		long y = assigned("[1,0]", null);
		long survivor = eventOf(y);
		long a = assigned("[3,4]", null);
		mergeInto(eventOf(a), survivor, a);
		this.jdbc.sql("update event_articles set opened = false where article_id = :article").param("article", a).update();

		rejudge(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50));

		// 대조 — 연 사건 표지만 거짓이면 같은 저장 후보(Y 0.42)로 새 사건 결과를 받아 떼어 낸다
		assertThat(eventOf(a)).isNotEqualTo(survivor);
		assertThat(assignment(a)).containsEntry("opened", true);
		assertThat(eventRow(survivor)).containsEntry("article_count", 1);
		assertThat(count("select count(*) from events where status = 'OPEN'")).isEqualTo(2);
	}

	@Test
	void aClosedEventKeepsItsAssignmentsUnderNewCriteria() {
		long a = assigned("[1,0]", null, "금리");
		long event = eventOf(a);
		// 0.7 × 0.8 + 0.3 × 1 = 0.86 / 중심 [2.5,1.5] 와 cos ≈ 0.857 → 0.7 × 0.857 + 0.3 ≈ 0.90 — 둘 다 산식으로 붙는다
		long b = assigned("[4,3]", null, "금리");
		long c = assigned("[1,0]", null, "금리");
		assertThat(List.of(eventOf(b), eventOf(c))).containsOnly(event);
		close(event);
		AssignmentCriteria raised = new AssignmentCriteria(Duration.ofHours(72), 0.97, 0.95, 0.40, 0.50);
		List<Long> rejudged = new ArrayList<>();

		rejudge(raised, LATER, recording(rejudged));

		assertThat(List.of(eventOf(a), eventOf(b), eventOf(c))).containsOnly(event);
		assertThat(eventRow(event)).containsEntry("article_count", 3);
		assertThat(count("select count(*) from events")).isEqualTo(1);
		long criteria = this.criteriaHistory.register(raised, LATER);
		assertThat(List.of(criteriaOf(a), criteriaOf(b), criteriaOf(c))).containsOnly(criteria);
		assertThat(assignment(b)).containsEntry("assigned", "2026-11-06T00:00:00Z");
		// 하나씩 다시 판정하기 전에 닫힌 사건의 배정을 한꺼번에 기준만 새것으로 적었다 — 하나씩 잠근 배정이 없다
		assertThat(rejudged).isEmpty();
	}

	@Test
	void anEventClosedAfterTheClosedBatchKeepsItsAssignmentsWhenLocked() {
		long a = assigned("[1,0]", null, "금리");
		long event = eventOf(a);
		long b = assigned("[4,3]", null, "금리");
		long c = assigned("[1,0]", null, "금리");
		List<Long> rejudged = new ArrayList<>();
		AtomicBoolean closed = new AtomicBoolean();
		// 닫힌 사건 일괄 유지 때는 열려 있던 E 가 하나씩 다시 판정하는 첫 트랜잭션 안에서 닫힌다
		EventArticleRepository closing = new ForwardingEventArticleRepository(recording(rejudged)) {

			@Override
			public List<RejudgeTarget> lockRejudgeTargets(long criteriaId, int limit) {
				if (closed.compareAndSet(false, true)) {
					close(event);
				}
				return super.lockRejudgeTargets(criteriaId, limit);
			}

		};

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.97, 0.95, 0.40, 0.50), LATER, closing);

		// 셋 모두 하나씩 잠겼고, 잠근 지금 사건이 닫혀 있어 떼어 내지 않고 기준만 새것으로 적는다
		assertThat(rejudged).containsExactly(a, b, c);
		assertThat(List.of(eventOf(a), eventOf(b), eventOf(c))).containsOnly(event);
		assertThat(eventRow(event)).containsEntry("article_count", 3);
		assertThat(count("select count(*) from events")).isEqualTo(1);
		assertThat(assignment(b)).containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat(criteriaOf(b)).isNotEqualTo(firstCriteria());
	}

	@Test
	void anOpenEventUnderTheSameCriteriaSplitsItsAttachedArticles() {
		long a = assigned("[1,0]", null, "금리");
		long event = eventOf(a);
		long b = assigned("[4,3]", null, "금리");
		long c = assigned("[1,0]", null, "금리");

		rejudge(new AssignmentCriteria(Duration.ofHours(72), 0.97, 0.95, 0.40, 0.50));

		// 대조 — 열린 사건이면 아래 기준 미만이 된 붙은 둘을 떼어 내고, 연 기사는 남는다
		assertThat(eventOf(a)).isEqualTo(event);
		assertThat(List.of(eventOf(b), eventOf(c))).doesNotContain(event).doesNotHaveDuplicates();
		assertThat(count("select count(*) from events where status = 'OPEN'")).isEqualTo(3);
	}

	@Test
	void aStoredCandidateOfAMergedEventIsReadAsTheSurvivor() {
		long y = assigned("[1,0]", null);
		long survivor = eventOf(y);
		long x = assigned("[0,1]", null);
		long merged = eventOf(x);
		long b = storedIn(survivor, 0.60, Map.of(survivor, 0.6, merged, 0.9), Map.of(survivor, 0.60, merged, 0.62));
		mergeInto(merged, survivor, x);

		rejudge(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50));

		// X(0.62 · 0.9) 를 Y 로 읽어 모은 Y(0.62 · 0.9) 에 판정으로 붙는다 — 지금 사건이라 다시 적는다
		assertThat(eventOf(b)).isEqualTo(survivor);
		assertThat(assignment(b)).containsEntry("decided_by", "EVALUATOR").containsEntry("assigned", "2026-11-06T01:00:00Z");
		assertThat((Double) assignment(b).get("score")).isCloseTo(0.62, within(1e-9));
	}

	@Test
	void aStoredCandidateOfAClosedEventIsNotMovedTo() {
		long y = assigned("[1,0]", null);
		long survivor = eventOf(y);
		long x = assigned("[0,1]", null);
		long closed = eventOf(x);
		long b = storedIn(survivor, 0.60, Map.of(survivor, 0.6, closed, 0.9), Map.of(survivor, 0.60, closed, 0.62));
		close(closed);

		rejudge(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50));

		// 대조 — 닫힌 사건은 남는 사건으로 읽지 않는다. 옮길 사건이 열려 있지 않아 유지한다
		assertThat(eventOf(b)).isEqualTo(survivor);
		assertThat(assignment(b)).containsEntry("assigned", "2026-11-06T00:00:00Z");
		assertThat((Double) assignment(b).get("score")).isCloseTo(0.60, within(1e-9));
	}

	/** 병합을 흉내 낸다 — 합쳐질 사건의 기사를 남는 사건으로 옮기고, 합쳐질 사건은 남는 사건을 가리킨다 */
	private void mergeInto(long merged, long survivor, long article) {
		this.jdbc.sql("update event_articles set event_id = :survivor where article_id = :article")
			.param("survivor", survivor)
			.param("article", article)
			.update();
		this.jdbc.sql("update events set article_count = article_count + 1 where id = :survivor")
			.param("survivor", survivor)
			.update();
		this.jdbc.sql("update events set status = 'MERGED', merged_into = :survivor, closed_at = now() where id = :merged")
			.param("survivor", survivor)
			.param("merged", merged)
			.update();
	}

	/** 하나씩 다시 판정하려고 잠근 배정의 기사 ID 를 적는 배정 저장소 */
	private EventArticleRepository recording(List<Long> rejudged) {
		return new ForwardingEventArticleRepository(this.assignments) {

			@Override
			public List<RejudgeTarget> lockRejudgeTargets(long criteriaId, int limit) {
				List<RejudgeTarget> targets = super.lockRejudgeTargets(criteriaId, limit);
				targets.forEach(target -> rejudged.add(target.assignment().articleId().value()));
				return targets;
			}

		};
	}

	private void close(long event) {
		this.jdbc.sql("update events set status = 'CLOSED', closed_at = now() where id = :event")
			.param("event", event)
			.update();
	}

	/** 사건에 판정으로 붙은 기사 하나를 SQL 로 넣는다 — 저장 후보의 점수 · 확률을 고른다 */
	private long storedIn(long event, double score, Map<Long, Double> probabilities, Map<Long, Double> scores) {
		long article = EventArticles.insert(this.jdbc, "재판정 시험 기사", null);
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, '2026-11-05T00:00:00Z', :score, 'EVALUATOR', false, :criteria, :now)
				""")
			.param("article", article)
			.param("event", event)
			.param("score", score)
			.param("criteria", firstCriteria())
			.param("now", NOW.atOffset(ZoneOffset.UTC))
			.update();
		scores.forEach((candidate, candidateScore) -> this.jdbc.sql("""
				insert into event_article_candidates (article_id, event_id, score, probability)
				values (:article, :event, :score, :probability)
				""")
			.param("article", article)
			.param("event", candidate)
			.param("score", candidateScore)
			.param("probability", probabilities.get(candidate))
			.update());
		this.jdbc.sql("update events set article_count = article_count + 1 where id = :event").param("event", event).update();
		return article;
	}

	/** 기사 하나를 넣어 배정 회차로 배정한다 — 띠에 들면 그 확률로 판정한다 */
	private long assigned(String vector, @Nullable Double probability, String... terms) {
		long article = EventArticles.insert(this.jdbc, "재판정 시험 기사", null);
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
		EventMembership membership = query -> {
			if (probability == null) {
				throw new AssertionError("묻지 않아야 한다: " + query);
			}
			return new MembershipJudgement.Judged(
					Map.of(query.candidates().get(0).eventId(), probability));
		};
		new AssignmentRound(this.queue, this.intake, this.features, this.events, this.assignments, this.criteriaHistory,
				CRITERIA, this.articles, membership, this.transaction, (InstantSource) () -> NOW)
			.run(new ModelOnly());
		return article;
	}

	private void rejudge(AssignmentCriteria criteria) {
		rejudge(criteria, LATER);
	}

	private void rejudge(AssignmentCriteria criteria, Instant now) {
		rejudge(criteria, now, this.assignments);
	}

	private void rejudge(AssignmentCriteria criteria, Instant now, EventArticleRepository assignments) {
		new RejudgementRound(assignments, this.events, this.features, this.criteriaHistory, criteria, this.shrinks,
				this.transaction, (InstantSource) () -> now)
			.run();
	}

	private void remove(long article) {
		EventIntakeListener listener = new EventIntakeListener(this.intake, this.queue, this.features, this.events,
				this.assignments, this.shrinks, () -> LATER);
		this.transaction
			.executeWithoutResult(status -> listener.on(new ArticlesRemoved(List.of(new ArticleId(article)))));
	}

	private long firstCriteria() {
		return this.criteriaHistory.register(CRITERIA, NOW);
	}

	private long eventOf(long article) {
		return this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private long criteriaOf(long article) {
		return this.jdbc.sql("select criteria_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private Map<String, @Nullable Object> assignment(long article) {
		return this.jdbc.sql("""
				select score, decided_by, opened,
				    to_char(assigned_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as assigned
				from event_articles where article_id = :article
				""").param("article", article).query().singleRow();
	}

	private Map<String, @Nullable Object> eventRow(long event) {
		return this.jdbc.sql("select centroid::text as centroid, article_count, model_version from events where id = :event")
			.param("event", event)
			.query()
			.singleRow();
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

}
