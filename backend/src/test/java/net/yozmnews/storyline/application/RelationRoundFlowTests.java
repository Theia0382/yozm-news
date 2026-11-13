package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.FlowRelation;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationOutcome;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.LinkedBy;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관계 회차의 흐름 — 후보 쌍 고르기(함께 가진 사건 · 점수 · 사건 수 하한 · 다시 묻기), 요청의 순서와 서술, 맺기와 지우기, 구조 제약, 실패
 * 갈래, 판정 중에 합쳐진 스토리라인, 쌍 수의 상한을 실제 PostgreSQL 과 실제 저장소 · 사건 조회로 본다. 관계 판정(`FlowRelation`)은 시험이
 * 넣는다. 사건과 스토리라인은 시험이 직접 심고, 대표 기사는 사건 ID + 100000 인 기사로 답하게 한다(사건에 기사를 넣지 않는다). 말의 무게는
 * 모두 같게 한다. 회차는 시험이 고정 시계로 직접 만든다(작업 서버는 끈다).
 * <p>
 * 기준: 확률 0.60 · 한 사안 0.20 · 사건 수 3 · 후보 점수 0.88 · 창 14일.
 * <p>
 * 지키는 규칙: storyline-R27 ~ storyline-R31
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class RelationRoundFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final String MODEL = "relation-m";

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14));

	private static final String EARLY = "2026-11-03T00:00:00Z";

	private static final String LATE = "2026-11-05T00:00:00Z";

	@Autowired
	EventDirectory directory;

	@Autowired
	StorylineRepository storylines;

	@Autowired
	LinkingCriteriaRepository linkingCriteria;

	@Autowired
	RelationRepository relations;

	@Autowired
	RelationQueueRepository queue;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<RelationQuery> asked = new ArrayList<>();

	/** 말 수 조회를 부른 횟수 */
	private int termCountCalls;

	/** 말 수 조회가 물은 말 — 부른 순서대로 */
	private final List<List<String>> termCountTerms = new ArrayList<>();

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void linksAPairThatSharesAnEventWithTheEarlierStorylineFirst() {
		long shared = event(EARLY, 2);
		long big = event(EARLY, 5);
		long[] parentEvents = { shared, big, event(EARLY, 1), event(EARLY, 1) };
		long parent = storyline("[1,0]", EARLY, LATE, parentEvents);
		long child = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(child, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.7, 0.9, 0.8, 41)).run();

		assertThat(this.relations.all()).containsExactly(new Relation(parent, child, RelationKind.CONTAINS));
		assertThat(relationRow()).containsEntry("probability", 0.7).containsEntry("judgement_id", 41L);
		assertThat(askedRow(parent, child)).containsEntry("outcome", "LINKED")
			.containsEntry("smaller_events", 3)
			.containsEntry("judgement_id", 41L);
		assertThat(queued()).isEmpty();
		// 먼저 시작한 parent 가 first 다. 서술은 기사 수가 많은 순으로 고르고 시각 순으로 싣는다 — 여기서는 시각이 같아 사건 ID 순
		RelationQuery query = this.asked.getFirst();
		assertThat(this.asked).hasSize(1);
		assertThat(query.first().flowId()).isEqualTo(parent);
		assertThat(query.first().happenings()).isEqualTo(4);
		assertThat(query.first().firstAt()).isEqualTo(Instant.parse(EARLY));
		assertThat(query.first().lastAt()).isEqualTo(Instant.parse(LATE));
		assertThat(query.first().described()).extracting(FlowHappening::articleId)
			.containsExactly(article(parentEvents[0]), article(parentEvents[1]), article(parentEvents[2]),
					article(parentEvents[3]));
		// 요약문은 기사 수가 많은 셋 — big(5) · shared(2) · 기사 1건 가운데 ID 가 작은 것
		assertThat(query.first().described()).extracting(FlowHappening::summarized)
			.containsExactly(true, true, true, false);
		assertThat(query.second().flowId()).isEqualTo(child);
		assertThat(query.second().happenings()).isEqualTo(3);
	}

	@Test
	void asksAPairWithoutSharedEventsOnlyWhenTheirScoreReachesTheCandidateScore() {
		// 중심이 같고 말이 같다 — 점수 1. 중심이 직각이면 코사인 하한에 못 미쳐 후보가 아니다
		long a = storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long near = storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		storyline("[0,1]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.FIRST_LED_TO_SECOND, 0.8, 0.9, 0.9, 42)).run();

		assertThat(this.asked).hasSize(1);
		// 시작이 같아 ID 가 작은 a 가 first 다
		assertThat(this.relations.all()).containsExactly(new Relation(a, near, RelationKind.FOLLOWS));
	}

	@Test
	void countsTermsOnlyForPartnersItCanAskAndOncePerSubjectInARound() {
		// 중심이 같은 스토리라인 넷 — a · b · c 는 사건 셋, small 은 사건 둘이라 물을 수 없다
		long a = storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long c = storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		storyline("[1,0]", EARLY, LATE, event(EARLY, 1), event(EARLY, 1));
		this.queue.enqueue(a, NOW);
		this.queue.enqueue(b, NOW);
		this.queue.enqueue(c, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 60)).run();

		// 쌍은 (a, b) · (a, c) · (b, c) 셋이다. 사건 수가 같아 판정할 쪽은 ID 가 큰 쪽 — b · c 둘의 말 수만 한 번씩 읽는다
		assertThat(this.asked).hasSize(3);
		assertThat(this.termCountCalls).isEqualTo(2);

		// 모두 물은 뒤에는 점수를 매길 상대가 없어 말 수를 묻지 않는다
		this.queue.enqueue(a, NOW);
		this.queue.enqueue(b, NOW);
		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 61)).run();
		assertThat(this.asked).hasSize(3);
		assertThat(this.termCountCalls).isEqualTo(2);
	}

	@Test
	void scoresFromTheStorylineWithFewerEventsWhicheverOneIsBeingProcessed() {
		// 코사인 2 ÷ √5 ≈ 0.8944 — 점수 = 0.89 × 0.8944 + 0.11 × 겹침. 겹침이 1 이면 0.906(후보), 1/3 이면 0.833(후보 아님).
		// 겹침은 판정할 쪽의 핵심 말 가운데 견줄 쪽에도 있는 몫이다 — 말이 하나인 쪽이 판정할 쪽이어야 1 이다
		long narrow = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long wide = storyline(new float[] { 2, 1 }, Map.of("금리", 4, "물가", 4, "환율", 4), event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1), event(EARLY, 1));
		// 사건이 많은 wide 를 처리한다 — 판정할 쪽은 사건이 적은 상대 narrow 이고, 말 수도 narrow 의 말로 묻는다
		this.queue.enqueue(wide, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 62)).run();

		assertThat(this.asked).hasSize(1);
		assertThat(this.asked.getFirst().first().flowId()).isEqualTo(narrow);
		assertThat(this.termCountTerms).containsExactly(List.of("금리"));
	}

	@Test
	void countsTheTermsOverTheWindowOfTheStorylineWithFewerEvents() {
		// narrow 는 11-03 하루, wide 는 11-03 ~ 11-05 다. 말 수는 판정할 쪽 narrow 의 구간(앞뒤 14일)으로 묻는다 — 10-20 ~ 11-17.
		// wide 의 구간으로 물으면 끝이 11-19 다
		long narrow = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, EARLY, event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long wide = storyline(new float[] { 2, 1 }, Map.of("금리", 4, "물가", 4, "환율", 4), EARLY, LATE, event(EARLY, 1),
				event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		this.queue.enqueue(wide, NOW);
		List<List<Instant>> windows = new ArrayList<>();
		// 말마다 수가 다른 조회 — 사건 10건 가운데 금리는 5건, 나머지 말은 1건
		EventDirectory uneven = new ForwardingEventDirectory(withRepresentatives()) {
			@Override
			public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
				windows.add(List.of(from, to));
				Map<String, Long> counts = new HashMap<>();
				terms.forEach(term -> counts.put(term, term.equals("금리") ? 5L : 1L));
				return new EventTermCounts(10, counts);
			}
		};
		FlowRelation recording = query -> {
			this.asked.add(query);
			return judged(RelationAnswer.NONE, 0.9, 0.9, 0.9, 64);
		};

		new RelationRound(this.queue, this.relations, this.storylines, uneven, recording, CRITERIA, this.transaction,
				InstantSource.fixed(NOW))
			.run();

		// 판정할 쪽의 말은 금리 하나라 무게와 무관하게 겹침 1 — 후보다
		assertThat(this.asked).hasSize(1);
		assertThat(this.asked.getFirst().first().flowId()).isEqualTo(narrow);
		assertThat(windows)
			.containsExactly(List.of(Instant.parse("2026-10-20T00:00:00Z"), Instant.parse("2026-11-17T00:00:00Z")));
	}

	@Test
	void doesNotAskWhenTheStorylineWithFewerEventsHasTermsTheOtherLacks() {
		// 대조 — 같은 코사인에서 말이 셋인 쪽이 사건이 적다. 판정할 쪽의 말 셋 가운데 하나만 겹쳐 겹침 1/3, 점수 0.833 — 후보가 아니다
		storyline(new float[] { 2, 1 }, Map.of("금리", 3, "물가", 3, "환율", 3), event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long many = storyline(new float[] { 1, 0 }, Map.of("금리", 4), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		this.queue.enqueue(many, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 63)).run();

		assertThat(this.asked).isEmpty();
		assertThat(this.termCountTerms).containsExactly(List.of("금리", "물가", "환율"));
	}

	@Test
	void doesNotAskAStorylineOrAPartnerBelowTheMinimumNumberOfEvents() {
		long shared = event(EARLY, 1);
		long small = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1));
		long large = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		this.queue.enqueue(small, NOW);
		this.queue.enqueue(large, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 43)).run();

		assertThat(this.asked).isEmpty();
		assertThat(this.relations.all()).isEmpty();
		assertThat(queued()).isEmpty();
	}

	@Test
	void recordsAnAnswerThatDoesNotLinkAndDoesNotAskThePairAgainUntilItDoubles() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.59, 0.9, 0.9, 44)).run();

		assertThat(this.relations.all()).isEmpty();
		assertThat(askedRow(a, b)).containsEntry("outcome", "LOW_PROBABILITY").containsEntry("smaller_events", 3);

		// 다시 들어와도 묻지 않는다 — 작은 쪽이 3 그대로다
		this.queue.enqueue(b, NOW);
		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 45)).run();
		assertThat(this.asked).hasSize(1);
		assertThat(queued()).isEmpty();

		// 두 스토리라인이 모두 사건 여섯이 되면 다시 묻는다 — 이번 답으로 맺는다
		grow(a, 3);
		grow(b, 3);
		this.queue.enqueue(b, NOW);
		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 46)).run();
		assertThat(this.asked).hasSize(2);
		assertThat(this.relations.all()).containsExactly(new Relation(a, b, RelationKind.CONTAINS));
		assertThat(askedRow(a, b)).containsEntry("outcome", "LINKED").containsEntry("smaller_events", 6);
	}

	@Test
	void aLaterAnswerReplacesOrRemovesTheRelationOfThePair() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(a, b, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.recordAsked(a, b, 1, 40L, criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(a, NOW);

		// 방향이 뒤집힌 답 — 자기 옛 관계와 부딪치지 않고 바뀐다
		round(answer(RelationAnswer.FIRST_PART_OF_SECOND, 0.8, 0.9, 0.9, 47)).run();
		assertThat(this.relations.all()).containsExactly(new Relation(b, a, RelationKind.CONTAINS));

		// 한 사안이 아니라는 답 — 관계를 지운다
		this.relations.recordAsked(a, b, 1, 47L, criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(a, NOW);
		round(answer(RelationAnswer.FIRST_PART_OF_SECOND, 0.8, 0.9, 0.19, 48)).run();
		assertThat(this.relations.all()).isEmpty();
		assertThat(askedRow(a, b)).containsEntry("outcome", "NOT_ONE_MATTER").containsEntry("judgement_id", 48L);
	}

	@Test
	void doesNotGiveAChildASecondParent() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		long other = storyline("[0,1]", EARLY, EARLY, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(other, b, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.recordAsked(other, b, 3, 40L, criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 49)).run();

		assertThat(this.relations.all()).containsExactly(new Relation(other, b, RelationKind.CONTAINS));
		assertThat(askedRow(a, b)).containsEntry("outcome", "SECOND_PARENT");
	}

	@Test
	void recordsAPairWithNothingToAskOrARejectedInputAndGoesOn() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);

		round(query -> new RelationJudgement.NothingToAsk()).run();
		assertThat(askedRow(a, b)).containsEntry("outcome", "NOTHING_TO_ASK").containsEntry("judgement_id", null);
		assertThat(queued()).isEmpty();

		this.jdbc.sql("delete from storyline_relation_asked").update();
		this.queue.enqueue(a, NOW);
		round(query -> new RelationJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED, "문맥 초과")).run();
		assertThat(askedRow(a, b)).containsEntry("outcome", "REJECTED");
		assertThat(queued()).isEmpty();

		// 대표 기사가 하나도 없는 흐름 — 평가기를 부르지 않고 물을 것 없음으로 남긴다
		this.jdbc.sql("delete from storyline_relation_asked").update();
		this.asked.clear();
		this.queue.enqueue(a, NOW);
		round(this.directory, answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 50)).run();
		assertThat(this.asked).isEmpty();
		assertThat(askedRow(a, b)).containsEntry("outcome", "NOTHING_TO_ASK");
	}

	@Test
	void aTransientFailureKeepsTheRowAndDropsItAfterThreeFailures(CapturedOutput output) {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);
		FlowRelation failing = query -> new RelationJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "잠시 실패");

		round(failing).run();
		assertThat(queueRow(a)).containsEntry("failures", 1).containsEntry("lease_token", null);
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();

		round(failing).run();
		round(failing).run();
		assertThat(queued()).isEmpty();
		assertThat(output.getAll()).contains("스토리라인 관계 판정이 거듭 실패해 대기에서 뺐다 — 스토리라인 " + a + ", 실패 3회");
	}

	@Test
	void anUnavailableEvaluatorStopsTheRoundAndLeavesTheLeases(CapturedOutput output) {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);
		this.queue.enqueue(b, NOW);

		round(query -> new RelationJudgement.Failed(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "키가 틀렸다")).run();

		// 첫 스토리라인에서 멈춘다 — 둘 다 점유된 채 남고 실패 수는 그대로다
		assertThat(this.asked).hasSize(1);
		assertThat(queueRow(a)).containsEntry("failures", 0).doesNotContainEntry("lease_token", null);
		assertThat(queueRow(b)).containsEntry("failures", 0).doesNotContainEntry("lease_token", null);
		assertThat(output.getAll()).contains("스토리라인 관계 판정을 멈췄다 — 평가기를 쓸 수 없다: 키가 틀렸다");
	}

	@Test
	void dropsAnAnswerForAStorylineThatWasMergedMeanwhile() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		long elsewhere = storyline("[0,1]", EARLY, EARLY, event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		this.queue.enqueue(a, NOW);
		FlowRelation merging = query -> {
			this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
				.param("into", elsewhere)
				.param("id", b)
				.update();
			return judged(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 51);
		};

		round(merging).run();

		assertThat(this.jdbc.sql("select count(*) from storyline_relations").query(Long.class).single()).isZero();
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();
	}

	@Test
	void asksFivePairsARoundAndKeepsTheRowWhileCandidatesRemain() {
		long shared = event(EARLY, 1);
		long hub = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		for (int i = 0; i < 6; i++) {
			storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		}
		this.queue.enqueue(hub, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 52)).run();
		assertThat(this.asked).hasSize(5);
		assertThat(queueRow(hub)).containsEntry("lease_token", null).containsEntry("failures", 0);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 53)).run();
		assertThat(this.asked).hasSize(6);
		assertThat(queued()).isEmpty();
	}

	@Test
	void doesNotAskAPartnerOfAnotherModelVersionEvenWhenTheyShareAnEvent() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.jdbc.sql("update storylines set model_version = 'relation-other' where id = :id").param("id", b).update();
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 55)).run();

		// 모델 버전이 다른 스토리라인은 사건을 함께 가졌어도 후보가 아니다(storyline-R27)
		assertThat(this.asked).isEmpty();
		assertThat(this.relations.all()).isEmpty();
		assertThat(queued()).isEmpty();
	}

	@Test
	void anUnexpectedExceptionLeavesTheRoundAndKeepsTheLease() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);

		assertThatIllegalStateException().isThrownBy(() -> round(query -> {
			throw new IllegalStateException("결과가 답 다섯과 어긋난다");
		}).run()).withMessage("결과가 답 다섯과 어긋난다");

		// 점유는 만료로 풀린다 — 실패 수는 올리지 않고 물은 기록도 없다(storyline-R31)
		assertThat(queueRow(a)).containsEntry("failures", 0).doesNotContainEntry("lease_token", null);
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();
	}

	@Test
	void aNotificationDuringTheRoundKeepsTheRow() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		this.queue.enqueue(a, NOW);
		FlowRelation again = query -> {
			this.queue.enqueue(a, NOW);
			return judged(RelationAnswer.NONE, 0.9, 0.9, 0.9, 54);
		};

		round(again).run();

		assertThat(queueRow(a)).containsEntry("notifications", 1).containsEntry("lease_token", null);
	}

	private RelationRound round(FlowRelation judge) {
		return round(withRepresentatives(), judge);
	}

	private RelationRound round(EventDirectory events, FlowRelation judge) {
		FlowRelation recording = query -> {
			this.asked.add(query);
			return judge.judge(query);
		};
		return new RelationRound(this.queue, this.relations, this.storylines, evenly(events), recording, CRITERIA,
				this.transaction, InstantSource.fixed(NOW));
	}

	private static FlowRelation answer(RelationAnswer top, double probability, double firstOneMatter,
			double secondOneMatter, long judgementId) {
		return query -> judged(top, probability, firstOneMatter, secondOneMatter, judgementId);
	}

	/** top 이 probability, 나머지는 0.01 */
	private static RelationJudgement judged(RelationAnswer top, double probability, double firstOneMatter,
			double secondOneMatter, long judgementId) {
		Map<RelationAnswer, Double> probabilities = new EnumMap<>(RelationAnswer.class);
		for (RelationAnswer answer : RelationAnswer.values()) {
			probabilities.put(answer, (answer == top) ? probability : 0.01);
		}
		return new RelationJudgement.Judged(new RelationJudgementId(judgementId), probabilities, firstOneMatter,
				secondOneMatter);
	}

	/** 사건마다 대표 기사를 사건 ID + 100000 으로 답하는 사건 조회 */
	private EventDirectory withRepresentatives() {
		return new ForwardingEventDirectory(this.directory) {
			@Override
			public Map<EventId, ArticleId> representativesOfAll(Collection<EventId> events) {
				Map<EventId, ArticleId> representatives = new HashMap<>();
				events.forEach(event -> representatives.put(event, article(event.value())));
				return representatives;
			}
		};
	}

	/** 말의 무게를 모두 같게 한 사건 조회 — 사건 수 1, 말마다 0건 */
	private EventDirectory evenly(EventDirectory events) {
		return new ForwardingEventDirectory(events) {
			@Override
			public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
				RelationRoundFlowTests.this.termCountCalls++;
				RelationRoundFlowTests.this.termCountTerms.add(terms.stream().sorted().toList());
				Map<String, Long> none = new HashMap<>();
				terms.forEach(term -> none.put(term, 0L));
				return new EventTermCounts(1, none);
			}
		};
	}

	private static ArticleId article(long event) {
		return new ArticleId(event + 100_000);
	}

	private long event(String at, int articles) {
		return StorylineRows.event(this.jdbc, "OPEN", MODEL, "[1,0]", articles, at, at, Map.of("금리", articles));
	}

	/** 저장소로 스토리라인을 심는다 — 핵심 말은 금리 */
	private long storyline(String centroid, String first, String last, long... events) {
		float[] vector = centroid.equals("[1,0]") ? new float[] { 1, 0 } : new float[] { 0, 1 };
		long storyline = this.storylines.open(new Representation(MODEL, vector, events.length, Instant.parse(first),
				Instant.parse(last), Map.of("금리", events.length), events[0]), NOW);
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80), NOW);
		for (long event : events) {
			this.storylines.addMember(storyline, event,
					LinkJudgement.judged(LinkedBy.PAIR, new Score(1.0, 1.0, 1.0), criteriaId, NOW));
		}
		return storyline;
	}

	/** 중심과 핵심 말을 정해 스토리라인을 심는다 — 시각은 EARLY ~ LATE */
	private long storyline(float[] centroid, Map<String, Integer> terms, long... events) {
		return storyline(centroid, terms, EARLY, LATE, events);
	}

	private long storyline(float[] centroid, Map<String, Integer> terms, String first, String last, long... events) {
		long storyline = this.storylines.open(new Representation(MODEL, centroid, events.length, Instant.parse(first),
				Instant.parse(last), terms, events[0]), NOW);
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80), NOW);
		for (long event : events) {
			this.storylines.addMember(storyline, event,
					LinkJudgement.judged(LinkedBy.PAIR, new Score(1.0, 1.0, 1.0), criteriaId, NOW));
		}
		return storyline;
	}

	private void grow(long storyline, int events) {
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80), NOW);
		for (int i = 0; i < events; i++) {
			this.storylines.addMember(storyline, event(LATE, 1),
					LinkJudgement.judged(LinkedBy.RULE, new Score(1.0, 1.0, 1.0), criteriaId, NOW));
		}
	}

	private List<Long> queued() {
		return this.jdbc.sql("select storyline_id from storyline_relation_queue order by storyline_id")
			.query(Long.class)
			.list();
	}

	private Map<String, @Nullable Object> queueRow(long storyline) {
		return this.jdbc.sql("""
				select notifications, failures, lease_token::text as lease_token from storyline_relation_queue
				where storyline_id = :id
				""").param("id", storyline).query().singleRow();
	}

	private Map<String, @Nullable Object> relationRow() {
		return this.jdbc.sql("select probability, judgement_id from storyline_relations").query().singleRow();
	}

	private Map<String, @Nullable Object> askedRow(long a, long b) {
		return this.jdbc.sql("""
				select outcome, smaller_events, judgement_id from storyline_relation_asked
				where low_id = :low and high_id = :high
				""").param("low", Math.min(a, b)).param("high", Math.max(a, b)).query().singleRow();
	}

}
