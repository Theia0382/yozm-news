package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.AssignmentRule;
import net.yozmnews.event.domain.assignment.AssignmentSchedule;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배정 흐름 — 배정 대기 → 후보 · 산식 · 사건 소속 판정 → 배정 · 사건 갱신을 실제 PostgreSQL 과 결선된 저장소 · 공개 조회로 본다.
 * <p>
 * 회차는 시험이 고정 시계 · 기준 · 기록 사건 소속 판정 대역을 넣어 직접 만든다(작업 서버는 끈다). 기사 표현은 시험이 고른 벡터 · 핵심 말로
 * SQL 로 넣고, 임베더는 모델 버전만 밝힌다(배정 회차는 임베딩하지 않는다). 벡터는 반 정밀도로 정확한 값을 쓴다. 기준은 위 0.80 · 아래
 * 0.55 · 저장 0.40 · 확률 0.50 · 창 72시간이다. 시험 문장은 지어낸 것이다.
 * <p>
 * 점수 = 감쇠 × (0.7 × 코사인 + 0.3 × 핵심 말 겹침). 같은 발행 시각이면 감쇠는 1 이다.
 * <p>
 * 지키는 규칙: event-R10 · event-R11 · event-R12 · event-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class EventAssignmentFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private static final Instant PUBLISHED = Instant.parse("2026-11-05T00:00:00Z");

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	private static final ArticleEmbedder MODEL_A = new ModelOnly("model-a");

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
	void theLimitsFitTheEventMembershipContract() {
		assertThat(AssignmentRule.MAX_ASKED).isLessThanOrEqualTo(MembershipQuery.MAX_CANDIDATES);
		assertThat(AssignmentSchedule.DESCRIBED_ARTICLES).isLessThanOrEqualTo(EventDescriptor.MAX_ARTICLES);
	}

	@Test
	void aLaterArticleOfTheSameRoundAttachesToTheEventTheFirstOpened() {
		long first = queued("[1,0]", "국회", "예산");
		long second = queued("[1,0]", "국회", "예산");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(Map.of()));

		round(membership).run(MODEL_A);

		long event = eventOf(first);
		assertThat(eventOf(second)).isEqualTo(event);
		// 새 사건을 연 기사는 연 사건 표지가 참, 붙인 기사는 거짓이다
		assertThat(assignment(first)).containsEntry("score", null)
			.containsEntry("decided_by", "FORMULA")
			.containsEntry("opened", true);
		// 0.7 × 1 + 0.3 × 2/2 = 1.0
		assertThat((Double) assignment(second).get("score")).isCloseTo(1.0, within(1e-6));
		assertThat(assignment(second)).containsEntry("decided_by", "FORMULA").containsEntry("opened", false);
		assertThat(eventRow(event)).containsEntry("centroid", "[1,0]")
			.containsEntry("article_count", 2)
			.containsEntry("status", "OPEN")
			.containsEntry("model_version", "model-a")
			.containsEntry("first", "2026-11-05T00:00:00Z")
			.containsEntry("last", "2026-11-05T00:00:00Z");
		assertThat(termCounts(event)).containsExactlyInAnyOrderEntriesOf(Map.of("국회", 2, "예산", 2));
		assertThat(candidates(second)).hasSize(1).first().satisfies(candidate -> {
			assertThat(candidate).containsEntry("event_id", event).containsEntry("probability", null);
		});
		assertThat(membership.queries).isEmpty();
		assertThat(queueRows()).isZero();
		assertThat(this.jdbc.sql("select count(distinct criteria_id) from event_articles").query(Long.class).single())
			.isEqualTo(1);
	}

	@Test
	void aBandArticleIsJudgedOutsideATransactionAndAttachedByTheEvaluator() {
		long first = queued("[1,0]");
		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);
		long event = eventOf(first);
		// [4,3] 과 [1,0] 의 코사인 0.8, 핵심 말 없음 — 0.7 × 0.8 = 0.56(띠)
		long banded = queued("[4,3]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(Map.of(event, 0.7)));

		round(membership).run(MODEL_A);

		assertThat(membership.queries).containsExactly(new MembershipQuery(new ArticleId(banded),
				List.of(new EventDescriptor(event, List.of(new ArticleId(first))))));
		assertThat(membership.inTransaction).containsExactly(false);
		assertThat(eventOf(banded)).isEqualTo(event);
		assertThat((Double) assignment(banded).get("score")).isCloseTo(0.56, within(1e-3));
		assertThat(assignment(banded)).containsEntry("decided_by", "EVALUATOR");
		assertThat(candidates(banded)).hasSize(1).first().satisfies(candidate -> {
			assertThat(candidate).containsEntry("event_id", event).containsEntry("probability", 0.7);
		});
		// ([1,0] + [4,3]) / 2
		assertThat(eventRow(event)).containsEntry("centroid", "[2.5,1.5]").containsEntry("article_count", 2);
	}

	@Test
	void aLowProbabilityOpensANewEventByTheEvaluator() {
		long first = queued("[1,0]");
		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);
		long event = eventOf(first);
		long banded = queued("[4,3]");

		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of(event, 0.3)))).run(MODEL_A);

		assertThat(eventOf(banded)).isNotEqualTo(event);
		assertThat((Double) assignment(banded).get("score")).isCloseTo(0.56, within(1e-3));
		assertThat(assignment(banded)).containsEntry("decided_by", "EVALUATOR");
		assertThat(candidates(banded)).hasSize(1).first().satisfies(candidate -> {
			assertThat(candidate).containsEntry("event_id", event).containsEntry("probability", 0.3);
		});
		assertThat(eventRow(event)).containsEntry("article_count", 1);
		assertThat(eventRow(eventOf(banded))).containsEntry("centroid", "[4,3]").containsEntry("article_count", 1);
	}

	@Test
	void timesFollowThePublicationAndOtherModelsAreNoCandidates() {
		long first = queued("[1,0]", "금리");
		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);
		long event = eventOf(first);
		// 다른 모델 버전의 열린 사건 — 중심이 같아도 후보가 아니다
		long otherModel = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-b', '[1,0]', 1, :t, :t, :t, :t) returning id
				""").param("t", PUBLISHED.atOffset(ZoneOffset.UTC)).query(Long.class).single();
		// 하루 뒤 발행 — 감쇠 1/2: 0.5 × (0.7 + 0.3) = 0.5 → 띠 아래라 새 사건 / 12시간 앞 발행 — 2^(−0.5) × 1 ≈ 0.707(띠)
		long later = queued("[1,0]", Instant.parse("2026-11-06T00:00:00Z"), "금리");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(Map.of(event, 0.9)));
		long earlier = queued("[1,0]", Instant.parse("2026-11-04T12:00:00Z"), "금리");

		round(membership).run(MODEL_A);

		assertThat(eventOf(later)).isNotIn(event, otherModel);
		assertThat((Double) assignment(later).get("score")).isCloseTo(0.5, within(1e-6));
		assertThat(eventOf(earlier)).isEqualTo(event);
		assertThat((Double) assignment(earlier).get("score")).isCloseTo(0.7071067811865476, within(1e-6));
		assertThat(eventRow(event)).containsEntry("first", "2026-11-04T12:00:00Z")
			.containsEntry("last", "2026-11-05T00:00:00Z")
			.containsEntry("article_count", 2);
		assertThat(membership.queries).extracting(MembershipQuery::articleId).containsExactly(new ArticleId(earlier));
	}

	@Test
	void anAssignedArticleQueuedAgainKeepsItsAssignment() {
		long first = queued("[1,0]");
		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);
		long event = eventOf(first);
		this.queue.enqueue(new ArticleId(first), NOW);

		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);

		assertThat(eventOf(first)).isEqualTo(event);
		assertThat(eventRow(event)).containsEntry("article_count", 1);
		assertThat(this.jdbc.sql("select count(*) from events").query(Long.class).single()).isEqualTo(1);
		assertThat(queueRows()).isZero();
	}

	@Test
	void anArticleWithoutTheCurrentModelRepresentationGoesBackToIntake() {
		long article = EventArticles.insert(this.jdbc, "모델이 바뀐 기사", null);
		represent(article, "model-old", "[1,0]");
		this.queue.enqueue(new ArticleId(article), NOW);

		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);

		assertThat(queueRows()).isZero();
		assertThat(this.jdbc.sql("select article_id from event_intake_queue").query(Long.class).list())
			.containsExactly(article);
		assertThat(this.jdbc.sql("select count(*) from event_articles").query(Long.class).single()).isZero();
	}

	@Test
	void aDeletedArticleLeavesTheQueue() {
		this.queue.enqueue(new ArticleId(987_654_321L), NOW);

		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);

		assertThat(queueRows()).isZero();
	}

	@Test
	void aTransientFailureRetriesAfterAMinuteAndTheFifthEndsInAFailedEvent() {
		long event = eventOf(firstEvent());
		long banded = queued("[4,3]");
		Scripted transientFailure = new Scripted(
				query -> new MembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시간 초과"));

		round(transientFailure).run(MODEL_A);

		assertThat(queueRow(banded)).containsEntry("attempts", 1)
			.containsEntry("available", "2026-11-06T00:01:00Z")
			.containsEntry("lease_token", null);
		assertThat(this.assignments.exists(new ArticleId(banded))).isFalse();

		this.jdbc.sql("update event_assignment_queue set attempts = 4, available_at = :now where article_id = :article")
			.param("now", NOW.atOffset(ZoneOffset.UTC))
			.param("article", banded)
			.update();
		round(transientFailure).run(MODEL_A);

		assertThat(eventOf(banded)).isNotEqualTo(event);
		assertThat((Double) assignment(banded).get("score")).isCloseTo(0.56, within(1e-3));
		assertThat(assignment(banded)).containsEntry("decided_by", "JUDGEMENT_FAILED");
		assertThat(candidates(banded)).hasSize(1).first().satisfies(candidate -> {
			assertThat(candidate).containsEntry("event_id", event).containsEntry("probability", null);
		});
		assertThat(queueRows()).isZero();
	}

	@Test
	void eachArticleUsesTheTimeItIsProcessed() {
		firstEvent();
		long banded = queued("[4,3]");
		// 회차 시작(점유)은 00:00, 그 뒤 시계는 00:05 — 기사를 처리한 때부터 1분 뒤에 다시 판정한다
		AtomicInteger reads = new AtomicInteger();
		InstantSource ticking = () -> (reads.getAndIncrement() == 0) ? NOW : NOW.plusSeconds(300);

		round(new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시간 초과")),
				ticking)
			.run(MODEL_A);

		assertThat(queueRow(banded)).containsEntry("attempts", 1).containsEntry("available", "2026-11-06T00:06:00Z");
	}

	@Test
	void aRejectedArticleEndsInAFailedEventAtOnce() {
		long event = eventOf(firstEvent());
		long banded = queued("[4,3]");

		round(new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED, "입력 거부")))
			.run(MODEL_A);

		assertThat(eventOf(banded)).isNotEqualTo(event);
		assertThat(assignment(banded)).containsEntry("decided_by", "JUDGEMENT_FAILED");
		assertThat(queueRows()).isZero();
	}

	@Test
	void anUnavailableEvaluatorStopsTheRoundAndReleasesTheRest(CapturedOutput output) {
		firstEvent();
		long banded = queued("[4,3]");
		long next = queued("[4,3]");
		Scripted membership = new Scripted(
				query -> new MembershipJudgement.Failed(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "키 무효"));

		round(membership).run(MODEL_A);

		assertThat(membership.queries).hasSize(1);
		assertThat(queueRow(banded)).containsEntry("attempts", 0).containsEntry("lease_token", null);
		assertThat(queueRow(next)).containsEntry("attempts", 0).containsEntry("lease_token", null);
		assertThat(this.assignments.exists(new ArticleId(banded))).isFalse();
		assertThat(output.getOut()).contains("평가기를 쓸 수 없어 배정 회차를 멈췄다 — 남은 기사 2건: 키 무효");
	}

	@Test
	void anUnexpectedFailureStopsTheRoundAndReleasesTheRest(CapturedOutput output) {
		firstEvent();
		long banded = queued("[4,3]");
		Scripted membership = new Scripted(query -> {
			throw new IllegalStateException("평가기가 없는 프로세스");
		});

		round(membership).run(MODEL_A);

		assertThat(queueRow(banded)).containsEntry("lease_token", null);
		assertThat(output.getOut()).contains("배정 회차가 예상하지 못한 오류로 멈췄다");
	}

	@Test
	void anArticleMissingDuringJudgementLeavesTheQueue() {
		firstEvent();
		long banded = queued("[4,3]");

		round(new Scripted(query -> new MembershipJudgement.ArticleMissing())).run(MODEL_A);

		assertThat(queueRows()).isZero();
		assertThat(this.assignments.exists(new ArticleId(banded))).isFalse();
	}

	@Test
	void anEventGoneDuringJudgementReleasesTheArticleForTheNextRound() {
		long first = firstEvent();
		long event = eventOf(first);
		long banded = queued("[4,3]");
		Scripted membership = new Scripted(query -> {
			this.jdbc.sql("delete from event_article_candidates where event_id = :event").param("event", event).update();
			this.jdbc.sql("delete from event_articles where event_id = :event").param("event", event).update();
			this.jdbc.sql("delete from events where id = :event").param("event", event).update();
			return new MembershipJudgement.Judged(Map.of(event, 0.9));
		});

		round(membership).run(MODEL_A);

		assertThat(this.assignments.exists(new ArticleId(banded))).isFalse();
		assertThat(queueRow(banded)).containsEntry("attempts", 0).containsEntry("lease_token", null);
		assertThat(this.jdbc.sql("select count(*) from events").query(Long.class).single()).isZero();
	}

	@Test
	void anEventThatHasNoDescribersIsNotAsked() {
		long first = firstEvent();
		long event = eventOf(first);
		this.jdbc.sql("delete from article_match_features where article_id = :article").param("article", first).update();
		long banded = queued("[4,3]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(Map.of()));

		round(membership).run(MODEL_A);

		assertThat(membership.queries).isEmpty();
		assertThat(eventOf(banded)).isNotEqualTo(event);
		assertThat(assignment(banded)).containsEntry("decided_by", "FORMULA");
	}

	/** 핵심 말 없는 [1,0] 기사로 사건 하나를 연다 */
	private long firstEvent() {
		long first = queued("[1,0]");
		round(new Scripted(query -> new MembershipJudgement.Judged(Map.of()))).run(MODEL_A);
		return first;
	}

	private AssignmentRound round(EventMembership membership) {
		return round(membership, () -> NOW);
	}

	private AssignmentRound round(EventMembership membership, InstantSource clock) {
		return new AssignmentRound(this.queue, this.intake, this.features, this.events, this.assignments,
				this.criteriaHistory, CRITERIA, this.articles, membership, this.transaction, clock);
	}

	private long queued(String vector, String... terms) {
		return queued(vector, PUBLISHED, terms);
	}

	private long queued(String vector, Instant publishedAt, String... terms) {
		long article = EventArticles.insert(this.jdbc, "배정 시험 기사", null);
		this.jdbc.sql("update articles set published_at = :published where id = :article")
			.param("published", publishedAt.atOffset(ZoneOffset.UTC))
			.param("article", article)
			.update();
		represent(article, "model-a", vector, terms);
		this.queue.enqueue(new ArticleId(article), NOW);
		return article;
	}

	private void represent(long article, String model, String vector, String... terms) {
		this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, :model, cast(:vector as vector), :terms, :now)
				""")
			.param("article", article)
			.param("model", model)
			.param("vector", vector)
			.param("terms", terms)
			.param("now", NOW.atOffset(ZoneOffset.UTC))
			.update();
	}

	private long eventOf(long article) {
		return this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private Map<String, @Nullable Object> assignment(long article) {
		return this.jdbc.sql("select score, decided_by, opened from event_articles where article_id = :article")
			.param("article", article)
			.query()
			.singleRow();
	}

	private Map<String, @Nullable Object> eventRow(long event) {
		return this.jdbc.sql("""
				select status, model_version, centroid::text as centroid, article_count,
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

	private List<Map<String, @Nullable Object>> candidates(long article) {
		return this.jdbc.sql("""
				select event_id, score, probability from event_article_candidates where article_id = :article
				order by event_id
				""").param("article", article).query().listOfRows();
	}

	private Map<String, @Nullable Object> queueRow(long article) {
		return this.jdbc.sql("""
				select attempts, lease_token,
				    to_char(available_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as available
				from event_assignment_queue where article_id = :article
				""").param("article", article).query().singleRow();
	}

	private long queueRows() {
		return this.jdbc.sql("select count(*) from event_assignment_queue").query(Long.class).single();
	}

	/** 모델 버전만 밝히는 임베더 — 배정 회차는 임베딩하지 않는다 */
	private record ModelOnly(String model) implements ArticleEmbedder {

		@Override
		public EmbedderInfo info() {
			return new EmbedderInfo("시험", this.model, 2);
		}

		@Override
		public List<Embedding> embed(List<String> texts) {
			throw new AssertionError("배정 회차는 임베딩하지 않는다");
		}

	}

	/** 정해 둔 답을 내고, 받은 요청과 그때 트랜잭션이 있었는지를 기록한다 */
	private static final class Scripted implements EventMembership {

		final List<MembershipQuery> queries = new ArrayList<>();

		final List<Boolean> inTransaction = new ArrayList<>();

		private final Function<MembershipQuery, MembershipJudgement> answer;

		Scripted(Function<MembershipQuery, MembershipJudgement> answer) {
			this.answer = answer;
		}

		@Override
		public MembershipJudgement judge(MembershipQuery query) {
			this.queries.add(query);
			this.inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
			return this.answer.apply(query);
		}

	}

}
