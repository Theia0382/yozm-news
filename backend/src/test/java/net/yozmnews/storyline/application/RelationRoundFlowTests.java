package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

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
import net.yozmnews.evaluation.FlowMembership;
import net.yozmnews.evaluation.FlowMembershipJudgement;
import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.evaluation.FlowMembershipQuery;
import net.yozmnews.evaluation.FlowRelation;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.AskedRecord;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.relation.GroupedBy;
import net.yozmnews.storyline.domain.relation.GroupedRelation;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
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
 * 기준: 확률 0.60 · 한 사안 0.20 · 사건 수 3 · 후보 점수 0.88 · 창 14일 · 합치기 확률 0.80.
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

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14), 0.80);

	/** 상위를 열 때의 평가에 쓰는 잇기 기준 — 띠 0.50 ~ 0.90 · 확률 0.60 */
	private static final LinkingCriteria OPENING = new LinkingCriteria(Duration.ofDays(14), 0.50, 0.90, 0.60);

	private static final String EARLY = "2026-11-03T00:00:00Z";

	private static final String LATE = "2026-11-05T00:00:00Z";

	@Autowired
	EventDirectory directory;

	@Autowired
	StorylineRepository storylines;

	@Autowired
	LinkingCriteriaRepository linkingCriteria;

	@Autowired
	LinkingAskedRepository linkAsked;

	@Autowired
	RelationRepository relations;

	@Autowired
	RelationQueueRepository queue;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<RelationQuery> asked = new ArrayList<>();

	/** 회차가 발행한 이벤트 */
	private final List<Object> published = new ArrayList<>();

	/** 상위를 열 때 흐름 소속 판정에 물은 것 */
	private final List<FlowMembershipQuery> membershipAsked = new ArrayList<>();

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

		new RelationRound(this.queue, this.relations, this.storylines, uneven, recording, CRITERIA, opening(uneven),
				merger(uneven), this.published::add, OPENING, this.linkingCriteria, this.linkAsked, this.transaction, InstantSource.fixed(NOW))
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
		// 상위가 없는 a 가 b 를 포함한다는 답이다 — b 의 상위와 a 의 쌍을 넘겨 묻는다(storyline-R38)
		assertThat(this.relations.referred(other)).containsExactly(a);
		assertThat(queued()).containsExactlyInAnyOrder(other, a);
	}

	@Test
	void aContainmentRefusedBetweenTwoChildrenOfDifferentParentsRefersNothing() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		long parentOfA = away(3);
		long parentOfB = away(3);
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(parentOfA, a, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.link(new Relation(parentOfB, b, RelationKind.CONTAINS), 0.7, 41, criteriaId, NOW);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 49)).run();

		assertThat(askedRow(a, b)).containsEntry("outcome", "SECOND_PARENT");
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_referrals").query(Long.class).single())
			.isZero();
		assertThat(queued()).isEmpty();
	}

	@Test
	void aContainmentRefusedToAnAncestorOfTheChildsParentRefersNothing() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		// a ⊃ between ⊃ b — b 의 상위는 이미 a 아래 있다
		long between = away(3);
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(a, between, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.link(new Relation(between, b, RelationKind.CONTAINS), 0.7, 41, criteriaId, NOW);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 49)).run();

		// a 가 b 를 포함한다는 답은 둘째 상위라 맺지 못한다. b 의 상위와 a 는 이미 포함으로 이어져 있어 그 쌍을 넘겨 묻지 않는다
		assertThat(askedRow(a, b)).containsEntry("outcome", "SECOND_PARENT");
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(a, between, RelationKind.CONTAINS),
				new Relation(between, b, RelationKind.CONTAINS));
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_referrals").query(Long.class).single())
			.isZero();
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
			throw new IllegalStateException("결과가 지금 문구 판의 답과 어긋난다");
		}).run()).withMessage("결과가 지금 문구 판의 답과 어긋난다");

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

	@Test
	void fragmentsBelowTheMergeProbabilityStayTwoWithoutARelationOrAParent() {
		long shared = event(EARLY, 1);
		long a = storyline("[1,0]", EARLY, LATE, shared, event(EARLY, 1), event(EARLY, 1));
		long b = storyline("[0,1]", LATE, LATE, shared, event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(a, b, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.queue.enqueue(a, NOW);

		// 조각 답은 상위를 열지 않는다 — 열기 평가를 부르면 실패하는 회차로 돈다. 확률 0.79 는 관계 기준(0.60)은 넘고 합치기
		// 기준(0.80)에는 못 미친다
		round(answer(RelationAnswer.SAME_MATTER, 0.79, 0.9, 0.9, 61)).run();

		assertThat(this.relations.all()).isEmpty();
		assertThat(storylineCount()).isEqualTo(2);
		assertThat(askedRow(a, b)).containsEntry("outcome", "UNMERGED").containsEntry("judgement_id", 61L);
		assertThat(queued()).isEmpty();
		// 둘로 남는다 — 소속도 그대로다
		assertThat(mergedInto(b)).isNull();
		assertThat(members(a)).hasSize(3);
		assertThat(members(b)).hasSize(3);
		assertThat(this.published).isEmpty();
	}

	@Test
	void mergesFragmentsAtOrAboveTheMergeProbabilityIntoTheSmallerId() {
		long shared = event(EARLY, 1);
		long[] first = { event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first[0], first[1], shared);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, shared, second[0], second[1]);
		// a 의 후보 쌍이 하나 더 있다 — 합친 뒤에는 이 회차에 묻지 않는다
		storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		// 잇기가 second[0] 을 a 에 물어 들지 않는다고 답한 기록 — 합치면 그 사건이 a 의 소속이 되므로 지운다
		this.linkAsked.recordLink(second[0], Map.of(a, new AskedRecord(new Asked(0.3, 5L), 1, 3)), NOW);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SAME_MATTER, 0.80, 0.9, 0.9, 61)).run();

		// b 가 a 에 합쳐졌다 — 합쳐진 행이 남는 쪽을 가리키고 판정 · 기준을 가진다
		assertThat(mergedInto(b)).isEqualTo(a);
		assertThat(this.jdbc.sql("select merge_judgement_id, merge_criteria_id from storylines where id = :id")
			.param("id", b)
			.query()
			.singleRow()).containsEntry("merge_judgement_id", 61L).containsEntry("merge_criteria_id", criteriaId);
		// b 의 소속 가운데 a 에 없던 사건이 근거 그대로 옮겨 왔다 — 함께 가진 사건은 a 의 소속 그대로 한 번만 든다
		assertThat(members(a)).containsExactlyInAnyOrder(first[0], first[1], shared, second[0], second[1]);
		assertThat(members(b)).isEmpty();
		assertThat(memberRow(a, second[0])).containsEntry("linked_by", "PAIR").containsEntry("score", 1.0);
		assertThat(mergedFrom(a, second[0])).isEqualTo(b);
		assertThat(mergedFrom(a, shared)).isNull();
		// 남는 쪽의 표현이 지금 구성 사건으로 다시 정해졌다 — 기사 수는 심을 때 3 이었고 기사 하나짜리 사건 다섯의 합은 5 다
		assertThat(this.storylines.profile(a, 20).orElseThrow().events()).isEqualTo(5);
		assertThat(this.jdbc.sql("select article_count from storylines where id = :id")
			.param("id", a)
			.query(Integer.class)
			.single()).isEqualTo(5);
		assertThat(this.jdbc.sql("select count(*) from storyline_link_asked where storyline_id = :id")
			.param("id", a)
			.query(Long.class)
			.single()).isZero();
		// 합친 쌍은 물은 기록이 없고, 그 스토리라인의 남은 쌍은 이 회차에 묻지 않는다
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();
		assertThat(this.asked).hasSize(1);
		assertThat(this.published)
			.containsExactly(new StorylinesRetired(List.of(new RetiredStoryline(new StorylineId(b), null))));
		// 남는 쪽은 관계 대기에 남아 다음 회차가 지금 구성으로 후보를 다시 고른다
		assertThat(queued()).containsExactly(a);
		assertThat(queueRow(a)).containsEntry("lease_token", null);
	}

	@Test
	void mergingMovesTheRelationsOfTheMergedSideAndDropsTheOnesThatCannotMove() {
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, event(LATE, 1), event(LATE, 1),
				event(LATE, 1));
		// 서로 다른 상위 아래의 두 조각, b 가 낳은 다른 스토리라인 하나 — 상위들은 점수로도 사건으로도 후보가 아니다
		long parentOfA = away(3);
		long parentOfB = away(3);
		long follower = away(3);
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(parentOfA, a, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.link(new Relation(parentOfB, b, RelationKind.CONTAINS), 0.7, 41, criteriaId, NOW);
		this.relations.link(new Relation(b, follower, RelationKind.FOLLOWS), 0.7, 42, criteriaId, NOW);
		this.queue.enqueue(b, NOW);

		round(answer(RelationAnswer.SAME_MATTER, 0.9, 0.9, 0.9, 61)).run();

		// 구조 제약으로 합치기를 막지 않는다 — b 의 이어짐은 a 로 옮기고, 둘째 상위가 되는 포함은 버린다(남는 쪽의 상위가 남는다)
		assertThat(mergedInto(b)).isEqualTo(a);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(parentOfA, a, RelationKind.CONTAINS),
				new Relation(a, follower, RelationKind.FOLLOWS));
		// 처리하던 쪽(b)이 합쳐졌다 — 그 대기 행은 사라지고 남는 쪽이 관계 대기에 든다
		assertThat(queued()).containsExactly(a);
	}

	@Test
	void mergingAnAncestorWithItsDescendantRemovesTheRelationBetweenThem() {
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, event(LATE, 1), event(LATE, 1),
				event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(a, b, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SAME_MATTER, 0.9, 0.9, 0.9, 61)).run();

		assertThat(mergedInto(b)).isEqualTo(a);
		assertThat(this.relations.all()).isEmpty();
		// 합쳐진 쪽을 끝으로 둔 행도 남지 않는다 — all() 은 합쳐진 끝의 행을 내지 않으므로 표를 직접 센다
		assertThat(this.jdbc.sql("select count(*) from storyline_relations").query(Long.class).single()).isZero();
	}

	@Test
	void aParallelPairThatMergesLeavesOneUnmarkedLinkToTheSurvivor() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, b, first[0], first[1], second[0], second[1]);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SAME_MATTER, 0.9, 0.9, 0.9, 70)).run();

		// 상위 → b 는 상위 → a 로 옮겨지며 이미 있어 버려지고, 맺은 쌍 (a, b) 는 한 스토리라인이 되어 표지가 지워진다
		assertThat(mergedInto(b)).isEqualTo(a);
		assertThat(this.relations.all()).containsExactly(new Relation(group, a, RelationKind.CONTAINS));
		assertThat(this.relations.grouped()).isEmpty();
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], second[0], second[1]);
	}

	@Test
	void aParallelPairAnsweredAsFragmentsBelowTheMergeProbabilityReleasesItsLinksLikeAnyOtherOverturn() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, b, first[0], first[1], second[0], second[1]);
		this.queue.enqueue(a, NOW);

		// 조각이지만 합치기 기준 미만 — 둘로 남는다
		round(answer(RelationAnswer.SAME_MATTER, 0.79, 0.9, 0.9, 69)).run();

		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, b, RelationKind.CONTAINS));
		assertThat(this.relations.grouped()).isEmpty();
		assertThat(this.relations.referred(group)).containsExactlyInAnyOrder(a, b);
		assertThat(askedRow(a, b)).containsEntry("outcome", "UNMERGED");
	}

	@Test
	void opensAParentOverTwoParallelFlowsWithTheEventsThatPassTheLinkingRule() {
		// 띠 0.50 ~ 0.90 · 확률 0.60. 가운데 사건들은 점수 0.94(위 기준 이상), band 는 0.77(띠), far 는 0.13(띠 아래)
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] core = { event(LATE, 1), event(LATE, 1) };
		long band = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0.6,0.8]", 1, LATE, LATE, Map.of("금리", 1));
		long far = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0,1]", 1, LATE, LATE, Map.of("환율", 1));
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 4), core[0], core[1], band, far);
		this.queue.enqueue(a, NOW);
		RelationRound round = groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61),
				membership(Map.of(band, 0.7), 71));

		round.run();

		long group = opened(a, b);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, b, RelationKind.CONTAINS));
		// 두 고리는 나란한 흐름 쌍 (a, b) 의 판정이 맺었다 — 확률 · 판정 원본도 그 판정 것
		assertThat(this.relations.of(a)).singleElement().satisfies(stored -> {
			assertThat(stored.groupedBy()).isEqualTo(GroupedBy.of(a, b));
			assertThat(stored.probability()).isEqualTo(0.9);
			assertThat(stored.judgementId()).isEqualTo(61);
		});
		assertThat(this.relations.of(b)).singleElement()
			.satisfies(stored -> assertThat(stored.groupedBy()).isEqualTo(GroupedBy.of(a, b)));
		// 상위에는 통과한 사건만 직접 든다 — 두 흐름의 소속은 그대로다
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], first[2], core[0], core[1], band);
		assertThat(members(a)).containsExactlyInAnyOrder(first[0], first[1], first[2]);
		assertThat(members(b)).containsExactlyInAnyOrder(core[0], core[1], band, far);
		assertThat(memberRow(group, band)).containsEntry("linked_by", "GROUP")
			.containsEntry("probability", 0.7)
			.containsEntry("judgement_id", 71L);
		assertThat(memberRow(group, first[0])).containsEntry("linked_by", "GROUP").containsEntry("probability", null);
		assertThat((Double) memberRow(group, band).get("score")).isCloseTo(0.773, within(0.005));
		// 띠 사건 하나만 물었다 — 흐름은 미리 받은 상위의 ID
		assertThat(this.membershipAsked).singleElement().satisfies(query -> {
			assertThat(query.articleId()).isEqualTo(article(band));
			assertThat(query.candidates()).singleElement()
				.satisfies(candidate -> assertThat(candidate.flowId()).isEqualTo(group));
		});
		assertThat(linkAskedRows(group)).containsExactly(Map.of("event_id", band, "judgement_id", 71L));
		assertThat(askedRow(a, b)).containsEntry("outcome", "GROUPED").containsEntry("judgement_id", 61L);
		assertThat(queued()).containsExactly(group);

		// 상위와 두 흐름은 사건을 함께 가졌지만 그 고리는 나란한 흐름 쌍의 판정을 따른다 — 따로 묻지 않는다
		round.run();

		assertThat(this.asked).hasSize(1);
		assertThat(queued()).isEmpty();
	}

	@Test
	void anAskedBandEventBelowTheProbabilityStaysOutOfTheParentButIsRecorded() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] core = { event(LATE, 1), event(LATE, 1) };
		long band = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0.6,0.8]", 1, LATE, LATE, Map.of("금리", 1));
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), core[0], core[1], band);
		this.queue.enqueue(a, NOW);

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), membership(Map.of(band, 0.59), 71)).run();

		long group = opened(a, b);
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], first[2], core[0], core[1]);
		assertThat(linkAskedRows(group)).containsExactly(Map.of("event_id", band, "judgement_id", 71L));
	}

	@Test
	void parallelFlowsWhosePassedEventsAreOnlyOneOfThemAreNotGroupedAndTheirEarlierContainmentIsRemoved() {
		// 한 흐름의 사건은 [1,0] 금리, 다른 흐름의 사건은 [0,1] 환율 — 모두 점수 0.60(띠)이라 묻는다
		long[] near = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] away = { away(), away(), away() };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), near);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), away);
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(a, b, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.queue.enqueue(a, NOW);
		Map<Long, Double> probabilities = new HashMap<>();
		for (long event : near) {
			probabilities.put(event, 0.9);
		}
		for (long event : away) {
			probabilities.put(event, 0.1);
		}

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), membership(probabilities, 71)).run();

		// 통과한 사건이 한 흐름의 구성 사건 그대로다 — 상위를 열지 않고 아무것도 남기지 않는다
		assertThat(this.membershipAsked).hasSize(6);
		assertThat(storylineCount()).isEqualTo(2);
		assertThat(this.relations.all()).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from storyline_link_asked").query(Long.class).single()).isZero();
		assertThat(askedRow(a, b)).containsEntry("outcome", "UNGROUPED").containsEntry("judgement_id", 61L);
		assertThat(queued()).isEmpty();
	}

	@Test
	void aParallelFlowIsReferredToAParentWithNoOtherChildAndJoinsItWhenThatPairIsParallelToo() {
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		// 상위는 점수로도 사건으로도 b 의 후보가 아니다
		long parent = storyline(new float[] { 0, 1 }, Map.of("환율", 3), event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(parent, a, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.recordAsked(parent, a, 3, 40L, criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(a, NOW);
		RelationRound round = groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61),
				membership(Map.of(), 71));

		round.run();

		// b 를 a 의 상위 아래 넣지 않는다 — (상위, b) 를 넘겨 물을 쌍으로 둔다
		assertThat(this.relations.all()).containsExactly(new Relation(parent, a, RelationKind.CONTAINS));
		assertThat(storylineCount()).isEqualTo(3);
		assertThat(askedRow(a, b)).containsEntry("outcome", "REFERRED");
		assertThat(this.relations.referred(parent)).containsExactly(b);
		assertThat(queued()).containsExactlyInAnyOrder(parent, b);

		round.run();

		// 넘겨 물은 쌍도 나란한 흐름이다 — 하위를 가진 상위 위에 상위를 열지 않는다. b 는 그 상위의 하위 a 와 나란한 흐름이므로
		// 그 상위의 하위가 된다. 고리는 (상위, b) 쌍 자신의 관계다(맺은 쌍 표지 없음)
		assertThat(storylineCount()).isEqualTo(3);
		assertThat(this.asked).hasSize(2);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(parent, a, RelationKind.CONTAINS),
				new Relation(parent, b, RelationKind.CONTAINS));
		assertThat(this.relations.of(b)).singleElement().satisfies(stored -> {
			assertThat(stored.groupedBy()).isNull();
			assertThat(stored.judgementId()).isEqualTo(61);
		});
		assertThat(askedRow(parent, b)).containsEntry("outcome", "GROUPED");
		assertThat(this.relations.referred(parent)).isEmpty();
		// 하위의 사건은 상위로 옮기지 않는다
		assertThat(members(parent)).hasSize(3);
		assertThat(this.membershipAsked).isEmpty();
	}

	@Test
	void aFlowParallelWithEveryChildOfAParentJoinsItWithoutOpeningAnotherParent() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long c = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, c, first[0], first[1], second[0], second[1]);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		// b 는 a 와만 물린다 — c 와는 다시 물을 때가 아닌 옛 기록이 없고 넘김으로만 물린다
		this.relations.recordAsked(a, c, 3, 61L, criteriaId, RelationOutcome.GROUPED, NOW);
		this.queue.enqueue(a, NOW);
		FlowRelation judge = query -> judged(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9,
				(query.first().flowId() == a || query.second().flowId() == a) ? 71 : 72);
		RelationRound round = groupingRound(judge, membership(Map.of(), 81));

		round.run();

		// (a, b) 는 나란한 흐름이고 a 의 상위에 다른 하위 c 가 있다 — 상위에 묻지 않고 (b, c) 를 넘겨 묻는다
		assertThat(askedRow(a, b)).containsEntry("outcome", "REFERRED").containsEntry("judgement_id", 71L);
		assertThat(this.relations.referred(b)).contains(c);
		assertThat(this.relations.referred(group)).isEmpty();
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, c, RelationKind.CONTAINS));

		round.run();

		// (b, c) 도 나란한 흐름이다 — 상위의 하위 모두와 나란하므로 b 를 그 상위의 하위로 둔다. 새 상위는 열지 않는다
		assertThat(storylineCount()).isEqualTo(4);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, c, RelationKind.CONTAINS), new Relation(group, b, RelationKind.CONTAINS));
		// 그 고리는 마지막으로 합의를 채운 쌍 (b, c) 의 판정이 맺은 것이다
		assertThat(this.relations.of(b))
			.filteredOn(stored -> stored.relation().equals(new Relation(group, b, RelationKind.CONTAINS)))
			.singleElement()
			.satisfies(stored -> {
				assertThat(stored.groupedBy()).isEqualTo(GroupedBy.of(b, c));
				assertThat(stored.judgementId()).isEqualTo(72);
			});
		assertThat(askedRow(b, c)).containsEntry("outcome", "GROUPED");
		assertThat(this.relations.referred(b)).isEmpty();
		// 하위의 사건은 상위로 옮기지 않고, 상위를 여는 평가도 부르지 않는다
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], second[0], second[1]);
		assertThat(this.membershipAsked).isEmpty();
	}

	@Test
	void aFlowThatIsNotParallelWithAChildOfTheParentStaysOutOfIt() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long c = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, c, first[0], first[1], second[0], second[1]);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.recordAsked(a, c, 3, 61L, criteriaId, RelationOutcome.GROUPED, NOW);
		this.queue.enqueue(a, NOW);
		// (a, b) 는 나란한 흐름, (b, c) 는 해당 없음
		FlowRelation judge = query -> (query.first().flowId() == a || query.second().flowId() == a)
				? judged(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 71)
				: judged(RelationAnswer.NONE, 0.9, 0.9, 0.9, 72);
		RelationRound round = groupingRound(judge, membership(Map.of(), 81));

		round.run();
		round.run();

		assertThat(askedRow(b, c)).containsEntry("outcome", "NOT_RELATED");
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, c, RelationKind.CONTAINS));
		assertThat(storylineCount()).isEqualTo(4);
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_referrals").query(Long.class).single())
			.isZero();
		assertThat(queued()).isEmpty();
	}

	@Test
	void aParallelPairWhoseOtherFlowAlreadyFailedWithAChildIsLeftUngroupedWithoutAskingMore() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long c = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, c, first[0], first[1], second[0], second[1]);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.recordAsked(a, c, 3, 61L, criteriaId, RelationOutcome.GROUPED, NOW);
		// b 와 c 는 앞서 물어 확률 미달이었다
		this.relations.recordAsked(b, c, 3, 70L, criteriaId, RelationOutcome.LOW_PROBABILITY, NOW);
		this.queue.enqueue(a, NOW);

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 71), membership(Map.of(), 81)).run();

		assertThat(this.asked).hasSize(1);
		assertThat(askedRow(a, b)).containsEntry("outcome", "UNGROUPED").containsEntry("judgement_id", 71L);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, c, RelationKind.CONTAINS));
		assertThat(storylineCount()).isEqualTo(4);
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_referrals").query(Long.class).single())
			.isZero();
	}

	@Test
	void anOverturnedParallelPairKeepsItsLinksAndRefersEachToItsOwnPair() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, b, first[0], first[1], second[0], second[1]);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 63)).run();

		// 나란한 흐름 판정이 뒤집혔다 — 고리는 그대로 두고 표지만 풀어 (상위, 하위) 쌍을 넘겨 묻는다
		assertThat(this.asked).hasSize(1);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(group, b, RelationKind.CONTAINS));
		assertThat(this.relations.grouped()).isEmpty();
		assertThat(this.relations.referred(group)).containsExactlyInAnyOrder(a, b);
		assertThat(askedRow(a, b)).containsEntry("outcome", "NOT_RELATED");
		assertThat(queued()).contains(group, b);
		// 상위의 사건은 걷어 내지 않는다
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], second[0], second[1]);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 64)).run();

		// 이제 각 고리는 자기 쌍의 판정을 따른다
		assertThat(this.asked).hasSize(3);
		assertThat(this.relations.all()).isEmpty();
		assertThat(this.relations.referred(group)).isEmpty();
		assertThat(askedRow(group, a)).containsEntry("outcome", "NOT_RELATED");
		assertThat(askedRow(group, b)).containsEntry("outcome", "NOT_RELATED");
	}

	@Test
	void aParallelPairAnsweredAsParallelAgainKeepsItsLinksAndAsFollowingReleasesBoth() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, b, first[0], first[1], second[0], second[1]);
		Relation toA = new Relation(group, a, RelationKind.CONTAINS);
		Relation toB = new Relation(group, b, RelationKind.CONTAINS);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 66)).run();

		// 다시 나란한 흐름이다 — 이미 같은 상위 아래다. 고리와 표지는 그대로이고 상위를 또 열지 않는다
		assertThat(this.relations.grouped()).containsExactly(new GroupedRelation(toA, GroupedBy.of(a, b)),
				new GroupedRelation(toB, GroupedBy.of(a, b)));
		assertThat(storylineCount()).isEqualTo(3);
		assertThat(this.relations.referred(group)).isEmpty();
		assertThat(askedRow(a, b)).containsEntry("outcome", "GROUPED").containsEntry("judgement_id", 66L);

		// 물은 기록을 지워 다시 묻게 한다 — 이번에는 이어짐
		this.relations.forgetAsked(a);
		this.queue.enqueue(a, NOW);
		round(answer(RelationAnswer.FIRST_LED_TO_SECOND, 0.9, 0.9, 0.9, 67)).run();

		assertThat(this.relations.all()).containsExactlyInAnyOrder(toA, toB, new Relation(a, b, RelationKind.FOLLOWS));
		assertThat(this.relations.grouped()).isEmpty();
		assertThat(this.relations.referred(group)).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void aReferredPairAnsweredAsAPartIsLinkedLikeAnyOtherPair() {
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, event(LATE, 1), event(LATE, 1),
				event(LATE, 1));
		long parent = storyline(new float[] { 0, 1 }, Map.of("환율", 3), EARLY, LATE, event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.link(new Relation(parent, a, RelationKind.CONTAINS), 0.7, 40, criteriaId, NOW);
		this.relations.recordAsked(parent, a, 3, 40L, criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(a, NOW);
		// (a, b) 는 나란한 흐름, 넘겨 물은 (상위, b) 는 b 가 상위의 부분
		FlowRelation judge = query -> (query.first().flowId() == parent || query.second().flowId() == parent)
				? judged(RelationAnswer.SECOND_PART_OF_FIRST, 0.8, 0.9, 0.9, 62)
				: judged(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61);
		RelationRound round = groupingRound(judge, membership(Map.of(), 71));

		round.run();
		round.run();

		assertThat(this.asked).hasSize(2);
		assertThat(this.asked.get(1).first().flowId()).isEqualTo(parent);
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(parent, a, RelationKind.CONTAINS),
				new Relation(parent, b, RelationKind.CONTAINS));
		assertThat(this.relations.of(b)).singleElement().satisfies(stored -> {
			assertThat(stored.groupedBy()).isNull();
			assertThat(stored.judgementId()).isEqualTo(62);
		});
		assertThat(this.relations.referred(parent)).isEmpty();
		assertThat(storylineCount()).isEqualTo(3);
	}

	@Test
	void aReferralThatCannotBeAskedIsDroppedWithoutAsking() {
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), event(LATE, 1));
		long parent = storyline(new float[] { 0, 1 }, Map.of("환율", 3), event(EARLY, 1), event(EARLY, 1),
				event(EARLY, 1));
		long small = storyline(new float[] { 0, 1 }, Map.of("환율", 2), event(EARLY, 1), event(EARLY, 1));
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		// (상위, b) 는 이미 물렸고 다시 물을 때가 아니다. (상위, small) 은 사건 수 하한 미만이다
		this.relations.recordAsked(parent, b, 3, 40L, criteriaId, RelationOutcome.NOT_RELATED, NOW);
		this.relations.refer(parent, b, NOW);
		this.relations.refer(parent, small, NOW);
		this.queue.enqueue(parent, NOW);

		round(answer(RelationAnswer.NONE, 0.9, 0.9, 0.9, 68)).run();

		assertThat(this.asked).isEmpty();
		assertThat(this.relations.referred(parent)).isEmpty();
		assertThat(askedRow(parent, b)).containsEntry("outcome", "NOT_RELATED").containsEntry("judgement_id", 40L);
	}

	@Test
	void aParallelPairOverturnedToContainmentMovesTheChildUnderTheOtherFlow() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] second = { event(LATE, 1), event(LATE, 1), event(LATE, 1) };
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), EARLY, LATE, first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), LATE, LATE, second);
		long group = grouped(a, b, first[0], first[1], second[0], second[1]);
		this.queue.enqueue(a, NOW);

		round(answer(RelationAnswer.SECOND_PART_OF_FIRST, 0.9, 0.9, 0.9, 65)).run();

		// b 가 a 의 부분이다 — 상위 → b 를 a → b 로 바꾸고, 상위 → a 만 표지를 풀어 넘겨 묻는다
		assertThat(this.relations.all()).containsExactlyInAnyOrder(new Relation(group, a, RelationKind.CONTAINS),
				new Relation(a, b, RelationKind.CONTAINS));
		assertThat(this.relations.grouped()).isEmpty();
		assertThat(this.relations.referred(group)).containsExactly(a);
		assertThat(askedRow(a, b)).containsEntry("outcome", "LINKED").containsEntry("judgement_id", 65L);
	}

	@Test
	void aTransientFailureWhileEvaluatingTheParentWritesNothingAndPostponesTheStoryline() {
		long band = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0.6,0.8]", 1, LATE, LATE, Map.of("금리", 1));
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), band);
		this.queue.enqueue(a, NOW);
		FlowMembership failing = query -> new FlowMembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "마감 초과");

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), failing).run();

		assertThat(storylineCount()).isEqualTo(2);
		assertThat(this.relations.all()).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();
		assertThat(queueRow(a)).containsEntry("failures", 1).containsEntry("lease_token", null);

		// 다음 회차에 평가가 답하면 열린다
		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), membership(Map.of(band, 0.7), 71)).run();

		assertThat(members(opened(a, b))).contains(band);
	}

	@Test
	void anEventTheEvaluatorRejectsWhileEvaluatingTheParentStaysOutAndIsRecordedWithoutAProbability() {
		long[] first = { event(EARLY, 1), event(EARLY, 1), event(EARLY, 1) };
		long[] core = { event(LATE, 1), event(LATE, 1) };
		long band = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0.6,0.8]", 1, LATE, LATE, Map.of("금리", 1));
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), first);
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), core[0], core[1], band);
		this.queue.enqueue(a, NOW);
		FlowMembership rejecting = query -> new FlowMembershipJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED,
				"문맥 초과");

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), rejecting).run();

		// 입력이 거부된 띠 사건은 들지 않는다 — 나머지로 상위를 열고, 그 사건은 확률 없이 물은 기록에 남는다
		long group = opened(a, b);
		assertThat(members(group)).containsExactlyInAnyOrder(first[0], first[1], first[2], core[0], core[1]);
		assertThat(this.jdbc
			.sql("select event_id, probability, judgement_id from storyline_link_asked where storyline_id = :storyline")
			.param("storyline", group)
			.query()
			.listOfRows()).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("event_id", band)
				.containsEntry("probability", null)
				.containsEntry("judgement_id", null));
		assertThat(askedRow(a, b)).containsEntry("outcome", "GROUPED");
	}

	@Test
	void anUnavailableEvaluatorWhileEvaluatingTheParentStopsTheRoundAndWritesNothing(CapturedOutput output) {
		long band = StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0.6,0.8]", 1, LATE, LATE, Map.of("금리", 1));
		long a = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(EARLY, 1), event(EARLY, 1), event(EARLY, 1));
		long b = storyline(new float[] { 1, 0 }, Map.of("금리", 3), event(LATE, 1), event(LATE, 1), band);
		this.queue.enqueue(a, NOW);
		FlowMembership unavailable = query -> new FlowMembershipJudgement.Failed(
				EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "키가 틀렸다");

		groupingRound(answer(RelationAnswer.PARALLEL, 0.9, 0.9, 0.9, 61), unavailable).run();

		// 회차를 멈춘다 — 상위도 물은 기록도 없고, 점유는 남고 실패 수는 그대로다
		assertThat(storylineCount()).isEqualTo(2);
		assertThat(this.relations.all()).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from storyline_relation_asked").query(Long.class).single()).isZero();
		assertThat(queueRow(a)).containsEntry("failures", 0).doesNotContainEntry("lease_token", null);
		assertThat(output.getAll()).contains("스토리라인 관계 판정을 멈췄다 — 평가기를 쓸 수 없다: 키가 틀렸다");
	}

	private RelationRound round(FlowRelation judge) {
		return round(withRepresentatives(), judge);
	}

	private RelationRound round(EventDirectory events, FlowRelation judge) {
		FlowRelation recording = query -> {
			this.asked.add(query);
			return judge.judge(query);
		};
		EventDirectory even = evenly(events);
		return new RelationRound(this.queue, this.relations, this.storylines, even, recording, CRITERIA, opening(even),
				merger(even), this.published::add, OPENING, this.linkingCriteria, this.linkAsked, this.transaction, InstantSource.fixed(NOW));
	}

	private StorylineMerger merger(EventDirectory events) {
		return new StorylineMerger(this.storylines, this.relations, this.queue, this.linkAsked, events);
	}

	/** 상위를 열 평가를 하지 않는 회차의 평가 — 하면 실패다 */
	private GroupOpening opening(EventDirectory events) {
		return new GroupOpening(events, this.storylines, query -> {
			throw new AssertionError("상위를 열 평가를 하지 않는다");
		}, this.transaction);
	}

	/** 나란한 흐름을 묶는 회차 — 상위를 열 때의 평가는 잇기 기준 {@link #OPENING} 으로 하고 물은 것을 모은다 */
	private RelationRound groupingRound(FlowRelation judge, FlowMembership membership) {
		FlowRelation recording = query -> {
			this.asked.add(query);
			return judge.judge(query);
		};
		FlowMembership recordingMembership = query -> {
			this.membershipAsked.add(query);
			return membership.judge(query);
		};
		EventDirectory even = evenly(withRepresentatives());
		return new RelationRound(this.queue, this.relations, this.storylines, even, recording, CRITERIA,
				new GroupOpening(even, this.storylines, recordingMembership, this.transaction), merger(even),
				this.published::add, OPENING,
				this.linkingCriteria, this.linkAsked, this.transaction, InstantSource.fixed(NOW));
	}

	/** 사건(대표 기사)마다 정한 확률로 답한다 — 정하지 않은 사건을 물으면 실패다 */
	private static FlowMembership membership(Map<Long, Double> byEvent, long judgementId) {
		return query -> {
			Double probability = byEvent.get(query.articleId().value() - 100_000);
			if (probability == null) {
				throw new AssertionError("물을 사건이 아니다: " + query.articleId());
			}
			return new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(judgementId),
					Map.of(query.candidates().getFirst().flowId(), probability));
		};
	}

	/** [0,1] · 환율 사건 */
	private long away() {
		return StorylineRows.event(this.jdbc, "OPEN", MODEL, "[0,1]", 1, LATE, LATE, Map.of("환율", 1));
	}

	/** 나란한 흐름 쌍 (a, b) 의 판정이 맺은 상위를 심는다 — 상위는 두 흐름의 사건을 직접 가진다 */
	private long grouped(long a, long b, long... events) {
		long group = storyline(new float[] { 1, 0 }, Map.of("금리", events.length), events);
		long criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.relations.linkGrouped(new Relation(group, a, RelationKind.CONTAINS), GroupedBy.of(a, b), 0.9, 61,
				criteriaId, NOW);
		this.relations.linkGrouped(new Relation(group, b, RelationKind.CONTAINS), GroupedBy.of(a, b), 0.9, 61,
				criteriaId, NOW);
		return group;
	}

	/** [0,1] · 환율 표현의 스토리라인 — [1,0] · 금리 스토리라인의 후보가 아니다 */
	private long away(int events) {
		long[] ids = new long[events];
		for (int i = 0; i < events; i++) {
			ids[i] = event(EARLY, 1);
		}
		return storyline(new float[] { 0, 1 }, Map.of("환율", events), ids);
	}

	private @Nullable Long mergedInto(long storyline) {
		return this.jdbc.sql("select merged_into from storylines where id = :id")
			.param("id", storyline)
			.query(Long.class)
			.optional()
			.orElse(null);
	}

	private @Nullable Long mergedFrom(long storyline, long event) {
		return this.jdbc.sql("select merged_from from storyline_members where storyline_id = :storyline and event_id = :event")
			.param("storyline", storyline)
			.param("event", event)
			.query(Long.class)
			.optional()
			.orElse(null);
	}

	/** 넘긴 스토리라인 말고 새로 열린 스토리라인 하나 */
	private long opened(long... known) {
		List<Long> ids = this.jdbc.sql("select id from storylines order by id").query(Long.class).list();
		for (long id : known) {
			ids.remove(Long.valueOf(id));
		}
		assertThat(ids).hasSize(1);
		return ids.getFirst();
	}

	private long storylineCount() {
		return this.jdbc.sql("select count(*) from storylines").query(Long.class).single();
	}

	private List<Long> members(long storyline) {
		return this.storylines.members(List.of(storyline)).getOrDefault(storyline, List.of());
	}

	private Map<String, @Nullable Object> memberRow(long storyline, long event) {
		return this.jdbc.sql("""
				select linked_by, score, probability, judgement_id from storyline_members
				where storyline_id = :storyline and event_id = :event
				""").param("storyline", storyline).param("event", event).query().singleRow();
	}

	private List<Map<String, @Nullable Object>> linkAskedRows(long storyline) {
		return this.jdbc.sql("""
				select event_id, judgement_id from storyline_link_asked where storyline_id = :storyline order by event_id
				""").param("storyline", storyline).query().listOfRows();
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
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80, 0.80, 0.50), NOW);
		for (long event : events) {
			this.storylines.addMember(storyline, event,
					LinkJudgement.judged(LinkedBy.PAIR, new Score(1.0, 1.0, 1.0), criteriaId, null, NOW));
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
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80, 0.80, 0.50), NOW);
		for (long event : events) {
			this.storylines.addMember(storyline, event,
					LinkJudgement.judged(LinkedBy.PAIR, new Score(1.0, 1.0, 1.0), criteriaId, null, NOW));
		}
		return storyline;
	}

	private void grow(long storyline, int events) {
		long criteriaId = this.linkingCriteria.register(new LinkingCriteria(Duration.ofDays(14), 0.80, 0.80, 0.50), NOW);
		for (int i = 0; i < events; i++) {
			this.storylines.addMember(storyline, event(LATE, 1),
					LinkJudgement.judged(LinkedBy.RULE, new Score(1.0, 1.0, 1.0), criteriaId, null, NOW));
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
