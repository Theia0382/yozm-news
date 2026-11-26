package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.yozmnews.RoundRunRows;
import net.yozmnews.TestClocks;
import net.yozmnews.TestClocks.MovableClock;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.StorylineCandidate;
import net.yozmnews.evaluation.StorylineMembership;
import net.yozmnews.evaluation.StorylineMembershipJudgement;
import net.yozmnews.evaluation.StorylineMembershipJudgementId;
import net.yozmnews.evaluation.StorylineMembershipQuery;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.platform.RoundRunner;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
 * 잇기의 띠 판정 — 띠 안 후보를 스토리라인 소속 판정에 묻고 물은 기록으로 정하는 흐름을 실제 PostgreSQL 과 실제 저장소 · 사건 조회로 본다.
 * 스토리라인 소속 판정은 시험이 넣거나(받은 요청을 모은다) 결선된 빈(로컬 대체물 평가기)을 쓴다. 회차는 시험이 고정 시계로 직접 만든다.
 * <p>
 * 기준: 창 14일 · 띠 [0.80, 0.95) · 확률 기준 0.50. 말의 무게는 모두 같게 한다 — 점수 = 0.89 × 코사인 + 0.11 × 겹침. 사건 [1,0] 둘은
 * 점수 1.0(위 기준 이상 — 묻지 않고 짝), [4,3] 은 [1,0] 과 코사인 0.8 · 겹침 1 이라 0.822(띠 안)이다.
 * <p>
 * 지키는 규칙: storyline-R3 · storyline-R4 · storyline-R6 · storyline-R10 · storyline-R11 · storyline-R14 · storyline-R36
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class LinkingBandFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final String MODEL = "band-m";

	private static final LinkingCriteria BANDED = new LinkingCriteria(Duration.ofDays(14), 0.80, 0.95, 0.50);

	private static final Map<String, Integer> TERMS = Map.of("금리", 1, "물가", 1);

	@Autowired
	RoundRunner runner;

	@Autowired
	EventDirectory directory;

	@Autowired
	StorylineRepository storylines;

	@Autowired
	LinkingQueueRepository queue;

	@Autowired
	LinkingCriteriaRepository criteria;

	@Autowired
	LinkingAskedRepository asked;

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	RelationRepository relations;

	@Autowired
	RelationQueueRepository relationQueue;

	@Autowired
	StorylineMembership wired;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	/** 사건 → 대표 기사 — 시험이 정한다 */
	private final Map<Long, ArticleId> representatives = new HashMap<>();

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
		this.jdbc.sql("delete from storyline_membership_judgements").update();
		this.representatives.clear();
	}

	@Test
	void aBandEventIsAskedOnceTakenByItsProbabilityAndTheRecordIsReusedUntilItDoubles() {
		Story story = storyWithBandEvent();
		Asking asking = new Asking(query -> judged(77, query, 0.71));

		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(asking.queries).containsExactly(new StorylineMembershipQuery(story.a3(),
				List.of(new StorylineCandidate(storyline, List.of(story.a1(), story.a2())))));
		assertThat(member(storyline, story.e3())).containsEntry("linked_by", "RULE")
			.containsEntry("probability", 0.71)
			.containsEntry("judgement_id", 77L);
		// 위 기준 이상으로 연 짝의 소속은 확률이 없다
		assertThat(member(storyline, story.e1())).containsEntry("linked_by", "PAIR").containsEntry("probability", null);
		assertThat(linkAsked(story.e3(), storyline)).containsEntry("probability", 0.71)
			.containsEntry("judgement_id", 77L)
			.containsEntry("event_articles", 1)
			.containsEntry("storyline_events", 2);

		// 같은 사건을 다시 판정하면 물은 기록을 쓴다 — 묻지 않는다
		this.queue.enqueue(story.e3(), Instant.parse("2026-11-05T06:00:00Z"), NOW);
		round(asking).run();
		assertThat(asking.queries).hasSize(1);

		// 사건의 기사 수가 물었을 때의 두 배가 되면 다시 묻는다
		this.jdbc.sql("update events set article_count = 2 where id = :id").param("id", story.e3()).update();
		this.queue.enqueue(story.e3(), Instant.parse("2026-11-05T06:00:00Z"), NOW);
		round(asking).run();
		assertThat(asking.queries).hasSize(2);
	}

	@Test
	void aBandEventBelowTheProbabilityThresholdStaysOutAndIsNotAskedAgain() {
		Story story = storyWithBandEvent();
		Asking asking = new Asking(query -> judged(78, query, 0.49));

		round(asking).run();
		this.queue.enqueue(story.e3(), Instant.parse("2026-11-05T06:00:00Z"), NOW);
		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		assertThat(linkAsked(story.e3(), storyline)).containsEntry("probability", 0.49);
		assertThat(asking.queries).hasSize(1);
		assertThat(queued(story.e3())).isEmpty();
	}

	@Test
	void aBandPartnerIsAskedAndOpensAStorylineWithTheProbability() {
		long a6 = article("부산 항만 파업");
		long a7 = article("부산 항만 파업 이틀째");
		long e6 = event("[1,0]", "2026-11-05T00:00:00Z", a6);
		long e7 = event("[4,3]", "2026-11-05T03:00:00Z", a7);
		Asking asking = new Asking(query -> judged(88, query, 0.9));

		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(asking.queries.get(0)).isEqualTo(
				new StorylineMembershipQuery(new ArticleId(a6), List.of(new StorylineCandidate(e7, List.of(new ArticleId(a7))))));
		assertThat(member(storyline, e6)).containsEntry("linked_by", "PAIR")
			.containsEntry("probability", 0.9)
			.containsEntry("judgement_id", 88L);
		assertThat(member(storyline, e7)).containsEntry("linked_by", "PAIR")
			.containsEntry("probability", 0.9)
			.containsEntry("judgement_id", 88L);
		assertThat(this.jdbc.sql("select probability, partner_articles from storyline_pair_asked where event_id = :e "
				+ "and partner_id = :p").param("e", e6).param("p", e7).query().singleRow())
			.containsEntry("probability", 0.9)
			.containsEntry("partner_articles", 1);
	}

	@Test
	void aTransientFailureDefersTheEventWithoutWritingAnything() {
		Story story = storyWithBandEvent();
		Asking asking = new Asking(
				query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시험이 넣은 일시 실패"));

		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		assertThat(askedRows()).isZero();
		assertThat(queued(story.e3())).hasValueSatisfying(row -> assertThat(row).containsEntry("transient_failures", 1)
			.containsEntry("failures", 0)
			.containsEntry("lease_token", null)
			.containsEntry("retry_at", Instant.parse("2026-11-10T00:01:00Z")));
		assertThat(storyline).isPositive();

		// 판정 가능 시각 전에는 다시 가져가지 않고, 그 시각이면 다시 묻는다
		round(asking, NOW.plusSeconds(59)).run();
		assertThat(asking.queries).hasSize(1);
		round(asking, NOW.plusSeconds(60)).run();
		assertThat(asking.queries).hasSize(2);
	}

	/**
	 * 평가기가 세 시간 동안 일시 실패로만 답해도 사건은 잇기 대기에서 빠지지 않고, 장애가 풀린 뒤 그 사건이 바뀌지 않아도 다시 묻는다. 장애
	 * 동안 회차는 1분마다 돈다 — 실패는 0 · 1 · 3 · 7 · 15 · 31 · 63 · 123분에 여덟 번이고, 간격은 1시간에서 멈춘다. 실패 수(예상 못한
	 * 예외)는 오르지 않는다.
	 */
	@Test
	void aLongRunOfTransientFailuresKeepsTheEventQueuedUntilTheEvaluatorRecovers(CapturedOutput output) {
		Story story = storyWithBandEvent();
		Asking outage = new Asking(
				query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시험이 넣은 일시 실패"));

		for (int minute = 0; minute <= 180; minute++) {
			round(outage, NOW.plus(Duration.ofMinutes(minute))).run();
		}

		assertThat(outage.queries).hasSize(8);
		assertThat(queued(story.e3())).hasValueSatisfying(row -> assertThat(row).containsEntry("transient_failures", 8)
			.containsEntry("failures", 0)
			.containsEntry("lease_token", null)
			.containsEntry("retry_at", NOW.plus(Duration.ofMinutes(183))));
		assertThat(output).doesNotContain("스토리라인 잇기 판정이 거듭 실패해 대기에서 뺐다");

		Asking recovered = new Asking(query -> judged(78, query, 0.71));
		round(recovered, NOW.plus(Duration.ofMinutes(183))).run();

		assertThat(recovered.queries).hasSize(1);
		assertThat(member(onlyStoryline(), story.e3())).containsEntry("linked_by", "RULE").containsEntry("probability", 0.71);
		assertThat(queued(story.e3())).isEmpty();
	}

	@Test
	void anUnavailableEvaluatorStopsTheRoundAndReleasesTheRemainingClaims(CapturedOutput output) {
		Story story = storyWithBandEvent();
		long a4 = article("서울 시장 선거 개표 다음 날");
		long e4 = event("[4,3]", "2026-11-05T09:00:00Z", a4);
		this.queue.enqueue(e4, Instant.parse("2026-11-05T09:00:00Z"), NOW);
		Asking asking = new Asking(query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE,
				"시험이 넣은 사용 불가"));

		round(asking).run();

		assertThat(asking.queries).hasSize(1);
		assertThat(queued(story.e3())).hasValueSatisfying(
				row -> assertThat(row).containsEntry("failures", 0).containsEntry("lease_token", null));
		assertThat(queued(e4)).hasValueSatisfying(
				row -> assertThat(row).containsEntry("failures", 0).containsEntry("lease_token", null));
		assertThat(output).contains("스토리라인 잇기 판정을 멈췄다 — 평가기를 쓸 수 없다: 시험이 넣은 사용 불가");
	}

	@Test
	void aRejectedArticleIsRecordedWithoutAProbabilityAndTheJudgementFinishes() {
		Story story = storyWithBandEvent();
		Asking asking = new Asking(
				query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED, "시험이 넣은 입력 거부"));

		round(asking).run();
		this.queue.enqueue(story.e3(), Instant.parse("2026-11-05T06:00:00Z"), NOW);
		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(linkAsked(story.e3(), storyline)).containsEntry("probability", null).containsEntry("judgement_id", null);
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		assertThat(queued(story.e3())).isEmpty();
		assertThat(asking.queries).hasSize(1);
	}

	@Test
	void aCandidateWithoutDescriptionsIsRecordedWithoutAskingTheEvaluator() {
		Story story = storyWithBandEvent();
		// 스토리라인의 구성 사건에 대표 기사가 없다 — 서술이 없어 묻지 못한다
		this.representatives.remove(story.e1());
		this.representatives.remove(story.e2());
		Asking asking = new Asking(query -> judged(79, query, 0.9));

		round(asking).run();

		long storyline = onlyStoryline();
		assertThat(asking.queries).isEmpty();
		assertThat(linkAsked(story.e3(), storyline)).containsEntry("probability", null);
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		// 짝을 연 사건의 다시 넣기가 처리 중인 e3 에 다시 알림을 올렸다 — 다음 회차가 기록으로 마치고 행을 지운다(storyline-R3)
		round(asking).run();
		assertThat(asking.queries).isEmpty();
		assertThat(queued(story.e3())).isEmpty();
	}

	@Test
	void anEventThatAsksOnEveryAttemptIsReleasedAfterThreeAttemptsWithoutAFailure() {
		Story story = storyWithBandEvent();
		// 물을 때마다 사건의 기사 수를 두 배로 — 다음 시도에서 방금 쓴 기록이 다시 물을 때가 되어 또 묻는다
		Asking asking = new Asking(query -> {
			this.jdbc.sql("update events set article_count = article_count * 2 where id = :id")
				.param("id", story.e3())
				.update();
			return judged(90, query, 0.3);
		});

		round(asking).run();

		assertThat(asking.queries).hasSize(3);
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		assertThat(queued(story.e3())).hasValueSatisfying(
				row -> assertThat(row).containsEntry("failures", 0).containsEntry("lease_token", null));
	}

	@Test
	void theWiredStorylineMembershipIsCalledOutsideTheTransaction(CapturedOutput output) {
		Story story = storyWithBandEvent();

		// 시험 설정의 평가기는 닿지 않는 주소의 Jev 라 일시 실패를 낸다 — 판정 가능 시각이 정해졌으면 결선된 판정 공개 API 가 트랜잭션
		// 확인을 지나 평가기까지 갔다. 트랜잭션 안에서 불렀다면 예상 못한 예외로 실패만 세고 판정 가능 시각이 없다
		round(this.wired).run();

		assertThat(queued(story.e3())).hasValueSatisfying(row -> assertThat(row).containsEntry("transient_failures", 1)
			.containsEntry("failures", 0)
			.containsEntry("retry_at", Instant.parse("2026-11-10T00:01:00Z")));
		assertThat(output).doesNotContain("스토리라인 잇기 판정이 예상하지 못한 오류로 실패했다");
		assertThat(askedRows()).isZero();
	}

	@Test
	void anEventMergedWhileAskingIsNotRecordedAndIsFollowed() {
		Story story = storyWithBandEvent();
		Asking asking = new Asking(query -> {
			StorylineRows.merge(this.jdbc, story.e3(), story.e1());
			return judged(80, query, 0.9);
		});

		round(asking).run();

		// 그새 합쳐진 사건의 답은 쓰지 않고, 다음 시도가 합쳐짐을 따른다
		assertThat(askedRows()).isZero();
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
		round(asking).run();
		assertThat(asking.queries).hasSize(1);
		assertThat(queued(story.e3())).isEmpty();
	}

	@Test
	void anAnswerIsNotRecordedForAnEventMergedMeanwhile() {
		Story story = storyWithBandEvent();
		// 짝(위 기준 이상)만 열고 띠 사건은 일시 실패로 남긴다 — 기록 없음
		round(new Asking(query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시험"))).run();
		long storyline = onlyStoryline();
		LinkingOutcome.Ask ask = new LinkingOutcome.Ask(LinkingOutcome.Target.LINK, story.e3(), story.a3(), 1,
				List.of(new LinkingOutcome.AskedCandidate(storyline, List.of(story.a1()), 2)));
		StorylineLinker linker = linker();

		// 대조 — 사건이 그대로면 쓴다
		this.transaction.executeWithoutResult(
				status -> linker.record(ask, new StorylineMembershipJudgementId(83), Map.of(storyline, 0.9), NOW));
		assertThat(askedRows()).isEqualTo(1);
		this.jdbc.sql("delete from storyline_link_asked").update();

		// 그새 합쳐졌으면 쓰지 않는다 — 다음 시도의 따르기를 기다리지 않고 기록 단계에서 막는다(R5)
		StorylineRows.merge(this.jdbc, story.e3(), story.e1());
		this.transaction.executeWithoutResult(
				status -> linker.record(ask, new StorylineMembershipJudgementId(84), Map.of(storyline, 0.9), NOW));
		assertThat(askedRows()).isZero();
	}

	@Test
	void aRemovedEventForgetsItsRecords() {
		Story story = storyWithBandEvent();
		round(new Asking(query -> judged(81, query, 0.71))).run();
		assertThat(askedRows()).isEqualTo(1);

		StorylineRows.delete(this.jdbc, story.e3());
		this.queue.enqueue(story.e3(), null, NOW);
		round(new Asking(query -> judged(82, query, 0.71))).run();

		assertThat(askedRows()).isZero();
		assertThat(this.storylines.storylinesOf(story.e3())).isEmpty();
	}

	@Test
	@Timeout(60)
	void aTransientFailureEndsTheRoundAfterItsBatch() {
		storyWithBandEvent();
		long last = fillersAfterTheStory(30);
		RoundRunRows.clear(this.jdbc);

		round(new Asking(query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "시험"))).run();

		// 첫 뭉치(사건 셋 · 채움 스물아홉)는 띠 사건의 일시 실패로 끝났다 — 마지막 채움은 손대지 않은 채 남는다
		assertThat(queued(last)).hasValueSatisfying(row -> assertThat(row).containsEntry("transient_failures", 0)
			.containsEntry("lease_token", null));
		assertThat(RoundRunRows.of(this.jdbc, "storyline-linking")).containsExactly("1 · 32 · BACKED_OFF");
	}

	@Test
	@Timeout(60)
	void anUnavailableEvaluatorEndsTheRoundAfterItsBatch() {
		storyWithBandEvent();
		long last = fillersAfterTheStory(30);
		RoundRunRows.clear(this.jdbc);
		Asking asking = new Asking(
				query -> new StorylineMembershipJudgement.Failed(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "시험"));

		round(asking).run();

		assertThat(asking.queries).hasSize(1);
		assertThat(queued(last)).hasValueSatisfying(row -> assertThat(row).containsEntry("lease_token", null));
		assertThat(RoundRunRows.of(this.jdbc, "storyline-linking")).containsExactly("1 · 32 · STOPPED");
	}

	/** 뭉치마다 그 뭉치의 지금으로 점유한다 — 둘째 뭉치(1분 뒤)의 점유 만료는 그 시각 + 50분이다 */
	@Test
	@Timeout(60)
	void eachBatchLeasesFromItsOwnTime() {
		fillersBeforeTheStory(30);
		Story story = storyWithBandEvent();
		MovableClock clock = TestClocks.movable(NOW);
		AtomicReference<Instant> leasedUntil = new AtomicReference<>();
		Asking asking = new Asking(query -> {
			leasedUntil.set(this.jdbc.sql("select lease_expires_at from storyline_linking_queue where event_id = :id")
				.param("id", story.e3())
				.query(OffsetDateTime.class)
				.single()
				.toInstant());
			return judged(81, query, 0.3);
		});
		LinkingRound round = new LinkingRound(this.queue, this.criteria, BANDED, linker(), asking, this.transaction, clock,
				this.runner);
		CriteriaOnce criteria = new CriteriaOnce();

		round.batch(criteria);
		clock.advance(Duration.ofMinutes(1));
		round.batch(criteria);

		assertThat(asking.queries).hasSize(1);
		assertThat(leasedUntil.get()).isEqualTo(Instant.parse("2026-11-10T00:51:00Z"));
	}

	/** 띠 사건보다 뒤의 채움 사건 {@code count} 개 — 서로 · 이야기와 15일 넘게 떨어져 홀로 남는다. 마지막 사건의 ID 를 낸다 */
	private long fillersAfterTheStory(int count) {
		return fillers(Instant.parse("2026-11-21T00:00:00Z"), count);
	}

	/** 이야기보다 앞의 채움 사건 {@code count} 개 — 서로 · 이야기와 15일 넘게 떨어져 홀로 남는다 */
	private void fillersBeforeTheStory(int count) {
		fillers(Instant.parse("2025-06-01T00:00:00Z"), count);
	}

	private long fillers(Instant first, int count) {
		long last = 0;
		for (int i = 0; i < count; i++) {
			String at = first.plus(Duration.ofDays(15L * i)).toString();
			last = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0,1]", 1, at, at, TERMS);
			this.queue.enqueue(last, Instant.parse(at), NOW);
		}
		return last;
	}

	/** 짝으로 열릴 [1,0] 둘과 그 스토리라인의 띠 안에 드는 [4,3] 하나 — 모두 대기에 넣는다 */
	private Story storyWithBandEvent() {
		long a1 = article("서울 시장 선거 개표");
		long a2 = article("서울 시장 선거 개표 결과");
		long a3 = article("서울 시장 선거 개표 마감");
		long e1 = event("[1,0]", "2026-11-05T00:00:00Z", a1);
		long e2 = event("[1,0]", "2026-11-05T03:00:00Z", a2);
		long e3 = event("[4,3]", "2026-11-05T06:00:00Z", a3);
		return new Story(e1, e2, e3, new ArticleId(a1), new ArticleId(a2), new ArticleId(a3));
	}

	private long article(String title) {
		return EventArticles.insert(this.jdbc, title, null);
	}

	/** 사건 하나를 넣고 대표 기사를 정하고 대기에 넣는다 */
	private long event(String centroid, String at, long article) {
		long event = StorylineRows.event(this.jdbc, "OPEN", MODEL, centroid, 1, at, at, TERMS);
		this.representatives.put(event, new ArticleId(article));
		this.queue.enqueue(event, Instant.parse(at), NOW);
		return event;
	}

	private LinkingRound round(StorylineMembership membership) {
		return round(membership, NOW);
	}

	private LinkingRound round(StorylineMembership membership, Instant at) {
		return new LinkingRound(this.queue, this.criteria, BANDED, linker(), membership, this.transaction,
				TestClocks.fixed(at), this.runner);
	}

	/** 말의 무게를 모두 같게 하고 대표 기사를 시험이 정한 사건 조회로 만든 판정 */
	private StorylineLinker linker() {
		EventDirectory events = new ForwardingEventDirectory(this.directory) {

			@Override
			public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
				Map<String, Long> none = new HashMap<>();
				terms.forEach(term -> none.put(term, 0L));
				return new EventTermCounts(1, none);
			}

			@Override
			public Map<EventId, ArticleId> representativesOfAll(Collection<EventId> ids) {
				Map<EventId, ArticleId> found = new HashMap<>();
				ids.forEach(id -> Optional.ofNullable(LinkingBandFlowTests.this.representatives.get(id.value()))
					.ifPresent(article -> found.put(id, article)));
				return found;
			}

		};
		return new StorylineLinker(events, this.storylines, this.queue, this.asked, this.relationQueue,
				new StorylineMerger(this.storylines, this.relations, this.relationQueue, this.asked, events), this.publisher);
	}

	private static StorylineMembershipJudgement judged(long judgement, StorylineMembershipQuery query, double probability) {
		Map<Long, Double> probabilities = new HashMap<>();
		query.candidates().forEach(candidate -> probabilities.put(candidate.candidateId(), probability));
		return new StorylineMembershipJudgement.Judged(new StorylineMembershipJudgementId(judgement), probabilities);
	}

	private long onlyStoryline() {
		return this.jdbc.sql("select id from storylines where model_version = :model and merged_into is null")
			.param("model", MODEL)
			.query(Long.class)
			.single();
	}

	private Map<String, Object> member(long storyline, long event) {
		return this.jdbc.sql("""
				select linked_by, probability, judgement_id from storyline_members
				where storyline_id = :storyline and event_id = :event
				""").param("storyline", storyline).param("event", event).query().singleRow();
	}

	private Map<String, Object> linkAsked(long event, long storyline) {
		return this.jdbc.sql("""
				select probability, judgement_id, event_articles, storyline_events from storyline_link_asked
				where event_id = :event and storyline_id = :storyline
				""").param("event", event).param("storyline", storyline).query().singleRow();
	}

	private long askedRows() {
		return this.jdbc.sql("select (select count(*) from storyline_link_asked) + (select count(*) from storyline_pair_asked)")
			.query(Long.class)
			.single();
	}

	private Optional<Map<String, Object>> queued(long event) {
		return this.jdbc.sql("""
				select notifications, failures, transient_failures, lease_token, retry_at
				from storyline_linking_queue where event_id = :event
				""").param("event", event).query().listOfRows().stream().findFirst().map(row -> {
			Map<String, Object> copy = new HashMap<>(row);
			Object retryAt = copy.get("retry_at");
			copy.put("retry_at", (retryAt == null) ? null : ((java.sql.Timestamp) retryAt).toInstant());
			return copy;
		});
	}

	/** 띠의 이야기 — 사건 셋과 그 대표 기사 */
	private record Story(long e1, long e2, long e3, ArticleId a1, ArticleId a2, ArticleId a3) {
	}

	/** 받은 요청을 모으고 정해진 답을 내는 스토리라인 소속 판정 */
	private static final class Asking implements StorylineMembership {

		private final Function<StorylineMembershipQuery, StorylineMembershipJudgement> answer;

		private final List<StorylineMembershipQuery> queries = new ArrayList<>();

		Asking(Function<StorylineMembershipQuery, StorylineMembershipJudgement> answer) {
			this.answer = answer;
		}

		@Override
		public StorylineMembershipJudgement judge(StorylineMembershipQuery query) {
			this.queries.add(query);
			return this.answer.apply(query);
		}

	}

}
