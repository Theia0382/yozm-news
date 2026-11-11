package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergedEvent;
import net.yozmnews.event.EventsChanged;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import net.yozmnews.event.domain.assignment.MergeJudgementRepository;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.EventQueryRepository;
import net.yozmnews.event.domain.event.EventRepository;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 병합 — 바뀐 열린 사건마다 가까운 열린 사건을 배정 산식 · 위 기준으로 합치고, 띠는 사건 소속 판정에 한 번 묻는다. 기사 수가 많은
 * 사건이 남고, 합쳐진 사건은 남는 사건을 가리킨다. 사건 · 배정 · 표현은 SQL 로 넣는다(기준 위 0.80 · 아래 0.55 · 저장 0.40 · 확률 0.50 ·
 * 창 72시간). 벡터는 반 정밀도로 정확한 값이다. 기록이 없는 띠 쌍은 배정 때 받은 확률(두 방향 · 최댓값)이 확률 기준 미만이면 묻지
 * 않고 합치지 않으며 남기지 않는다. 기준 이상이면 묻는다 — 앞선 확률로는 합치지 않는다.
 * <p>
 * 지키는 규칙: event-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class MergeFlowTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	private static final AssignmentCriteria CRITERIA = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	@Autowired
	ApplicationEvents published;

	@Autowired
	EventRepository events;

	@Autowired
	EventQueryRepository queries;

	@Autowired
	EventArticleRepository assignments;

	@Autowired
	MergeJudgementRepository judgements;

	@Autowired
	CriteriaRepository criteriaHistory;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	EventReview review;

	@BeforeEach
	@AfterEach
	void reset() {
		EventArticles.clear(this.jdbc);
	}

	@Test
	void twoCloseEventsMergeIntoTheLargerOne() {
		long large = event("[1,0]", Instant.parse("2026-11-05T02:00:00Z"), Instant.parse("2026-11-05T03:00:00Z"),
				Map.of("금리", 3, "동결", 3), "[1,0]", "[1,0]", "[1,0]");
		// cos([1,0], [0.8,0.6]) = 0.8, 작은 쪽 핵심 말(금리)이 모두 큰 쪽에 있다 — 0.7 × 0.8 + 0.3 × 1 = 0.86 ≥ 위 기준
		long small = event("[0.8,0.6]", Instant.parse("2026-11-05T01:00:00Z"), Instant.parse("2026-11-05T04:00:00Z"),
				Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> {
			throw new AssertionError("산식으로 합치므로 묻지 않는다: " + query);
		});

		this.published.clear();

		round(membership).run();

		// 합치기 — 남는 사건과 합쳐진 사건을 함께 알린다(event-R23)
		assertThat(changes()).containsExactly(List.of(large, small));
		assertThat(eventRow(small)).containsEntry("status", "MERGED")
			.containsEntry("merged_into", large)
			.containsEntry("closed", "2026-11-06T00:00:00Z");
		Map<String, @Nullable Object> merged = eventRow(large);
		assertThat(merged).containsEntry("status", "OPEN")
			.containsEntry("article_count", 4)
			.containsEntry("opened", "2026-11-05T01:00:00Z")
			.containsEntry("joined", "2026-11-05T04:00:00Z");
		// ([1,0] × 3 + [0.8,0.6] × 1) / 4 = [0.95, 0.15]
		float[] centroid = centroid(large);
		assertThat((double) centroid[0]).isCloseTo(0.95, within(1e-3));
		assertThat((double) centroid[1]).isCloseTo(0.15, within(1e-3));
		assertThat(termCounts(large)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 4, "동결", 3));
		assertThat(termCounts(small)).isEmpty();
		assertThat(this.jdbc.sql("select event_id, opened from event_articles order by article_id")
			.query()
			.listOfRows()).containsExactly(Map.of("event_id", large, "opened", true),
					Map.of("event_id", large, "opened", false), Map.of("event_id", large, "opened", false),
					Map.of("event_id", large, "opened", true));
		assertThat(membership.queries).isEmpty();
	}

	@Test
	void theAbsorbedEventKeepsTheRepresentativeItHadBeforeItsArticlesMoved() {
		long large = event("[1,0]", T, T, Map.of("금리", 3), "[1,0]", "[1,0]", "[1,0]");
		// 합쳐질 사건의 기사 둘 — 중심 [0.8,0.6] 에 가까운 것은 둘째다. 0.7 × 0.8 + 0.3 × 1 = 0.86 ≥ 위 기준
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 2), "[0,1]", "[0.8,0.6]");
		List<Long> members = this.jdbc.sql("select article_id from event_articles where event_id = :event order by article_id")
			.param("event", small)
			.query(Long.class)
			.list();
		long nearest = members.get(1);

		round(new Scripted(query -> {
			throw new AssertionError("산식으로 합치므로 묻지 않는다: " + query);
		})).run();

		assertThat(eventRow(small)).containsEntry("status", "MERGED").containsEntry("merged_into", large);
		assertThat(representativeColumn(small)).isEqualTo(nearest);
		// 남는 사건은 열려 있어 대표를 남기지 않는다
		assertThat(representativeColumn(large)).isNull();
		assertThat(this.review.mergedInto(new EventId(large))).extracting(MergedEvent::id, MergedEvent::representative)
			.containsExactly(tuple(new EventId(small), new ArticleId(nearest)));
		// 사건 기사 전체의 대표 조회에는 합쳐진 사건이 나오지 않는다
		assertThat(this.queries.representativesOfAll(List.of(small))).isEmpty();
	}

	@Test
	void aBandPairIsAskedOnceAndMergedByTheEvaluator() {
		// 핵심 말이 겹치지 않는다 — 0.7 × 0.8 = 0.56(띠)
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(
				query -> new MembershipJudgement.Judged(new EventJudgementId(71), Map.of(small, 0.8)));
		List<Long> largeMembers = members(large);
		List<Long> smallMembers = members(small);
		// 합쳐질 사건의 배정이 반영한 판정 원본 — 옮겨도 그대로다
		this.jdbc.sql("update event_articles set judgement_id = 72 where event_id = :event").param("event", small).update();

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		MembershipQuery query = membership.queries.get(0);
		// 판정할 기사는 바뀐 사건(large — 먼저 본다)의 서술 기사, 후보는 상대 사건과 그 서술 기사다
		assertThat(query.articleId().value()).isIn(largeMembers);
		assertThat(query.candidates()).singleElement().satisfies(candidate -> {
			assertThat(candidate.eventId()).isEqualTo(small);
			assertThat(candidate.articleIds()).extracting(id -> id.value()).containsExactlyElementsOf(smallMembers);
		});
		assertThat(membership.inTransaction).containsExactly(false);
		assertThat(eventRow(small)).containsEntry("status", "MERGED").containsEntry("merged_into", large);
		// 쌍의 기록은 확률을 낸 판정 원본을 가리킨다
		assertThat(judgementRows()).containsExactly(
				Map.of("event_low", large, "event_high", small, "probability", 0.8, "judgement_id", 71L));
		assertThat(this.jdbc.sql("select event_id, judgement_id from event_articles where article_id = any(:articles)")
			.param("articles", smallMembers.toArray(Long[]::new))
			.query()
			.listOfRows()).containsExactly(Map.of("event_id", large, "judgement_id", 72L));
	}

	@Test
	void aLowProbabilityKeepsThePairApartAndIsNotAskedAgain() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(
				query -> new MembershipJudgement.Judged(new EventJudgementId(1), Map.of(small, 0.3)));

		round(membership).run();
		// 두 사건이 모두 바뀌었다고 표시해도 남은 확률로 정해 다시 묻지 않는다
		this.jdbc.sql("update events set revision = revision + 1").update();
		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(large)).containsEntry("status", "OPEN").containsEntry("article_count", 2);
		assertThat(eventRow(small)).containsEntry("status", "OPEN").containsEntry("article_count", 1);
		assertThat(this.jdbc.sql("select count(*) from events where merge_checked_revision = revision")
			.query(Long.class)
			.single()).isEqualTo(2);
	}

	@Test
	void aPriorProbabilityAtOrAboveTheThresholdIsAskedInsteadOfMerging() {
		// 띠 쌍(0.56) — 바뀐 사건(large)의 기사가 배정 때 small 을 후보로 받아 0.8 을 남겼다. 앞선 확률로는 합치지 않고 묻는다
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		prior(members(large).get(0), small, 0.8);
		Scripted membership = answering(small, 0.1);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).containsExactly(
				Map.of("event_low", large, "event_high", small, "probability", 0.1, "judgement_id", 71L));
	}

	@Test
	void aPriorProbabilityFromTheOtherSideIsAskedToo() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 상대 사건(small)의 기사가 배정 때 바뀐 사건(large)을 후보로 받았다
		prior(members(small).get(0), large, 0.8);
		Scripted membership = answering(small, 0.9);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "MERGED").containsEntry("merged_into", large);
		assertThat(judgementRows()).containsExactly(
				Map.of("event_low", large, "event_high", small, "probability", 0.9, "judgement_id", 71L));
	}

	@Test
	void aPriorProbabilityForAMergedEventCountsForItsSurvivor() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// large 의 기사가 받은 후보 사건 x 는 그 뒤 small 에 합쳐졌다 — (large, small) 쌍의 앞선 확률 0.8 이라 묻는다
		long x = mergedInto(small);
		prior(members(large).get(0), x, 0.8);
		Scripted membership = answering(small, 0.1);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
	}

	@Test
	void aLowPriorProbabilityForAMergedEventKeepsItsSurvivorApart() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// large 의 기사가 받은 후보 사건 x 는 그 뒤 small 에 합쳐졌다 — (large, small) 쌍의 앞선 확률 0.2 라 묻지 않는다
		long x = mergedInto(small);
		prior(members(large).get(0), x, 0.2);
		Scripted membership = answering(small, 0.9);

		round(membership).run();

		assertThat(membership.queries).isEmpty();
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).isEmpty();
	}

	@Test
	void aPriorProbabilityForAnotherOpenEventIsNotUsedForThePair() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 다른 열린 사건 — 어느 사건과도 점수가 저장 기준 아래라 후보가 아니다
		long other = event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		prior(members(large).get(0), other, 0.8);
		Scripted membership = answering(small, 0.1);

		round(membership).run();

		// (large, small) 쌍의 앞선 확률이 없어 지금처럼 묻는다
		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).singleElement().satisfies(row -> {
			assertThat(row).containsEntry("event_low", large).containsEntry("event_high", small);
			assertThat(row).containsEntry("probability", 0.1).containsEntry("judgement_id", 71L);
		});
	}

	@Test
	void aLowPriorProbabilityKeepsThePairApartWithoutAskingOrRecording() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		long asked = members(large).get(0);
		prior(asked, small, 0.2);
		// 그 확률을 낸 판정 원본은 그 기사의 배정이 가리킨다
		this.jdbc.sql("update event_articles set judgement_id = 73 where article_id = :article")
			.param("article", asked)
			.update();
		Scripted membership = answering(small, 0.9);

		round(membership).run();

		assertThat(membership.queries).isEmpty();
		assertThat(eventRow(large)).containsEntry("status", "OPEN");
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).isEmpty();
		// 원본 하나 · 쓰임 하나 — 배정만 그 원본을 가리킨다(event-R22)
		Map<EventJudgementId, JudgementUse> uses = this.review.judgementUses(List.of(new EventJudgementId(73)));
		assertThat(uses).containsOnlyKeys(new EventJudgementId(73));
		assertThat(uses.get(new EventJudgementId(73))).isInstanceOf(JudgementUse.Assignment.class);
	}

	@Test
	void aPriorProbabilityAtTheThresholdIsAsked() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 확률 기준(0.50)과 같다 — 경계는 기준 이상이다
		prior(members(large).get(0), small, 0.50);
		Scripted membership = answering(small, 0.1);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
	}

	@Test
	void aPriorProbabilityJustBelowTheThresholdIsNotAsked() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		prior(members(large).get(0), small, 0.4999);
		Scripted membership = answering(small, 0.9);

		round(membership).run();

		assertThat(membership.queries).isEmpty();
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).isEmpty();
	}

	@Test
	void aLoweredThresholdDoesNotMergeOnAnEarlierPriorProbability() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		prior(members(large).get(0), small, 0.45);
		Scripted membership = answering(small, 0.1);

		// 확률 기준 0.50 — 0.45 는 기준 미만이라 묻지 않고 합치지 않는다
		round(membership).run();

		assertThat(membership.queries).isEmpty();
		assertThat(eventRow(small)).containsEntry("status", "OPEN");

		// 확률 기준을 0.40 으로 낮춘다 — 기준이 바뀌어 두 사건을 다시 보고, 0.45 는 이제 기준 이상이라 묻는다
		round(membership, new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.40)).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).containsExactly(
				Map.of("event_low", large, "event_high", small, "probability", 0.1, "judgement_id", 71L));
	}

	@Test
	void theHighestPriorProbabilityOfBothSidesIsCompared() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 0.2 만 보면 묻지 않지만 최댓값 0.7 은 기준 이상이다
		prior(members(large).get(0), small, 0.2);
		prior(members(small).get(0), large, 0.7);
		Scripted membership = answering(small, 0.1);

		round(membership).run();

		assertThat(membership.queries).hasSize(1);
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(judgementRows()).containsExactly(
				Map.of("event_low", large, "event_high", small, "probability", 0.1, "judgement_id", 71L));
	}

	@Test
	void onlyBandCandidatesWithoutALowPriorProbabilityAreAsked() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// large 와 띠(0.7 × 0.8 = 0.56), small 과는 0.7 × 0.28 = 0.196 — 저장 기준 아래
		long third = event("[0.8,-0.6]", T, T, Map.of("환율", 1), "[0.8,-0.6]");
		prior(members(large).get(0), small, 0.2);
		Scripted membership = answering(third, 0.1);

		round(membership).run();

		// large 를 볼 때 small 은 기준 미만 앞선 확률이라 묻지 않고, 앞선 확률이 없는 third 만 묻는다
		assertThat(membership.queries).singleElement()
			.satisfies(query -> assertThat(query.candidates()).extracting(candidate -> candidate.eventId())
				.containsExactly(third));
	}

	@Test
	void aLowPriorProbabilityDoesNotKeepAPairAboveTheBandApart() {
		// 0.86 ≥ 위 기준 — 앞선 확률(0.2)이 확률 기준 미만이어도 산식으로 합친다. 앞선 확률은 띠 쌍에만 쓴다
		long large = event("[1,0]", Instant.parse("2026-11-05T02:00:00Z"), Instant.parse("2026-11-05T03:00:00Z"),
				Map.of("금리", 3, "동결", 3), "[1,0]", "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", Instant.parse("2026-11-05T01:00:00Z"), Instant.parse("2026-11-05T04:00:00Z"),
				Map.of("금리", 1), "[0.8,0.6]");
		prior(members(large).get(0), small, 0.2);
		Scripted membership = new Scripted(query -> {
			throw new AssertionError("산식으로 합치므로 묻지 않는다: " + query);
		});
		List<List<Long>> lookedUp = new ArrayList<>();
		EventArticleRepository recording = new ForwardingEventArticleRepository(this.assignments) {
			@Override
			public Map<Long, Double> priorProbabilities(long eventId, Collection<Long> others) {
				lookedUp.add(List.copyOf(others));
				return super.priorProbabilities(eventId, others);
			}
		};

		new MergeRound(this.events, this.queries, recording, this.judgements, this.criteriaHistory, CRITERIA, membership,
				this.transaction, (InstantSource) () -> NOW)
			.run();

		assertThat(eventRow(small)).containsEntry("status", "MERGED").containsEntry("merged_into", large);
		assertThat(judgementRows()).isEmpty();
		// 띠 밖 후보는 앞선 확률을 찾지 않는다
		assertThat(lookedUp).isEmpty();
	}

	@Test
	void aMergedRowFollowsTheNextSurvivor() {
		long x = event("[1,0]", T, T, Map.of("금리", 1), "[1,0]");
		long y = event("[1,0]", T, T, Map.of("금리", 2), "[1,0]", "[1,0]");
		long z = event("[1,0]", T, T, Map.of("금리", 4), "[1,0]", "[1,0]", "[1,0]", "[1,0]");

		// x 를 보며 점수가 같은 y · z 가운데 ID 가 작은 y 에 합치고(y 가 남는다), z 를 보며 y 를 z 에 합친다(z 가 더 크다)
		round(new Scripted(query -> {
			throw new AssertionError("묻지 않는다");
		})).run();

		assertThat(eventRow(x)).containsEntry("status", "MERGED").containsEntry("merged_into", z);
		assertThat(eventRow(y)).containsEntry("status", "MERGED").containsEntry("merged_into", z);
		assertThat(eventRow(z)).containsEntry("status", "OPEN").containsEntry("article_count", 7);
		assertThat(this.jdbc.sql("select distinct event_id from event_articles").query(Long.class).list())
			.containsExactly(z);
	}

	@Test
	void runningAgainChangesNothing() {
		long large = event("[1,0]", T, T, Map.of("금리", 3, "동결", 3), "[1,0]", "[1,0]", "[1,0]");
		event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Judged(null, Map.of()));
		round(membership).run();
		// 남는 사건은 revision 이 늘어 한 번 더 본다 — 그 뒤로는 볼 것이 없다
		round(membership).run();
		List<Map<String, Object>> before = snapshot();

		round(membership).run();

		assertThat(snapshot()).isEqualTo(before);
		assertThat(eventRow(large)).containsEntry("article_count", 4);
		assertThat(membership.queries).isEmpty();
	}

	@Test
	void aTransientFailureLeavesTheEventUncheckedAndTheRoundGoesOn(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		// 떨어진 다른 쌍 — 산식으로 합친다
		long other = event("[0,1]", T, T, Map.of("환율", 2), "[0,1]", "[0,1]");
		long otherSmall = event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.TRANSIENT,
				"요청 제한"));

		round(membership).run();

		assertThat(this.jdbc.sql("select merge_checked_revision from events where id = :id")
			.param("id", large)
			.query(Long.class)
			.single()).isZero();
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(eventRow(otherSmall)).containsEntry("status", "MERGED").containsEntry("merged_into", other);
		assertThat(judgementRows()).isEmpty();
		assertThat(output.getOut()).contains("사건 병합 판정이 일시 실패해 다음 회차에 다시 본다 — 사건 " + large + ": 요청 제한");
	}

	@Test
	void anUnavailableEvaluatorStopsTheRound(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		long other = event("[0,1]", T, T, Map.of("환율", 2), "[0,1]", "[0,1]");
		long otherSmall = event("[0,1]", T, T, Map.of("환율", 1), "[0,1]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(
				EvaluatorFailureKind.EVALUATOR_UNAVAILABLE, "키가 무효다"));

		round(membership).run();

		// 첫 사건에서 멈춰 뒤의 쌍은 보지 않았다
		assertThat(eventRow(otherSmall)).containsEntry("status", "OPEN");
		assertThat(eventRow(other)).containsEntry("article_count", 2);
		assertThat(output.getOut()).contains("평가기를 쓸 수 없어 병합 회차를 멈췄다 — 사건 " + large + ": 키가 무효다");
	}

	@Test
	void aRejectedArticleDecidesWithoutAProbabilityAndMarksTheEvent(CapturedOutput output) {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.Failed(EvaluatorFailureKind.ARTICLE_REJECTED,
				"본문 거부"));

		round(membership).run();

		// 띠 후보는 확률 없이 붙일 수 없다 — 합치지 않고, 두 사건 모두 확인을 표시해 다음 회차에 다시 묻지 않는다
		assertThat(membership.queries).hasSize(2);
		assertThat(eventRow(large)).containsEntry("status", "OPEN").containsEntry("article_count", 2);
		assertThat(eventRow(small)).containsEntry("status", "OPEN").containsEntry("article_count", 1);
		assertThat(this.jdbc.sql("select count(*) from events where merge_checked_revision = revision")
			.query(Long.class)
			.single()).isEqualTo(2);
		assertThat(judgementRows()).isEmpty();
		assertThat(output.getOut()).contains("평가기가 기사를 거부해 병합 판정을 확률 없이 정한다 — 사건 " + large + ": 본문 거부");
	}

	@Test
	void aMissingArticleLeavesTheEventsUnchecked() {
		long large = event("[1,0]", T, T, Map.of("국회", 2), "[1,0]", "[1,0]");
		long small = event("[0.8,0.6]", T, T, Map.of("금리", 1), "[0.8,0.6]");
		Scripted membership = new Scripted(query -> new MembershipJudgement.ArticleMissing());

		round(membership).run();

		// 판정할 기사가 그새 지워졌다 — 표시하지 않아 다음 회차가 다른 서술 기사로 다시 묻는다
		assertThat(membership.queries).hasSize(2);
		assertThat(eventRow(large)).containsEntry("status", "OPEN");
		assertThat(eventRow(small)).containsEntry("status", "OPEN");
		assertThat(this.jdbc.sql("select merge_checked_revision from events order by id").query(Long.class).list())
			.containsExactly(0L, 0L);
		assertThat(judgementRows()).isEmpty();
	}

	private MergeRound round(EventMembership membership) {
		return round(membership, CRITERIA);
	}

	private MergeRound round(EventMembership membership, AssignmentCriteria criteria) {
		return new MergeRound(this.events, this.queries, this.assignments, this.judgements, this.criteriaHistory, criteria,
				membership, this.transaction, (InstantSource) () -> NOW);
	}

	/**
	 * 열린 사건 하나를 SQL 로 넣는다 — 구성 기사마다 기사 · 표현(모델 a) · 배정(첫 기사가 연 기사)을 넣는다. 첫 · 마지막 기사 시각은 T.
	 * @param opened 연 시각
	 * @param joined 마지막으로 붙은 시각
	 */
	private long event(String centroid, Instant opened, Instant joined, Map<String, Integer> terms,
			String... memberVectors) {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast(:centroid as halfvec), :count, :t, :t, :opened, :joined) returning id
				""")
			.param("centroid", centroid)
			.param("count", memberVectors.length)
			.param("t", T.atOffset(ZoneOffset.UTC))
			.param("opened", opened.atOffset(ZoneOffset.UTC))
			.param("joined", joined.atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		terms.forEach((term, count) -> this.jdbc
			.sql("insert into event_key_terms (event_id, term, article_count) values (:event, :term, :count)")
			.param("event", event)
			.param("term", term)
			.param("count", count)
			.update());
		long criteria = this.criteriaHistory.register(CRITERIA, T);
		for (int i = 0; i < memberVectors.length; i++) {
			long article = EventArticles.insert(this.jdbc, "병합 시험 기사", null);
			this.jdbc.sql("""
					insert into article_match_features (article_id, model_version, embedding, entities, created_at)
					values (:article, 'model-a', cast(:vector as vector), '{}', :t)
					""").param("article", article).param("vector", memberVectors[i]).param("t", T.atOffset(ZoneOffset.UTC))
				.update();
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

	/** 기사가 배정 때 받은 후보 사건과 그 확률 — 저장 후보 행 */
	private void prior(long article, long event, double probability) {
		this.jdbc.sql("""
				insert into event_article_candidates (article_id, event_id, score, probability)
				values (:article, :event, 0.6, :probability)
				""").param("article", article).param("event", event).param("probability", probability).update();
	}

	/** {@code survivor} 에 합쳐진 사건 하나 — 배정은 이미 남는 사건으로 옮겨 남지 않았다 */
	private long mergedInto(long survivor) {
		return this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, closed_at, merged_into)
				values ('MERGED', 'model-a', cast('[0,1]' as halfvec), 1, :t, :t, :t, :t, :t, :survivor) returning id
				""").param("survivor", survivor).param("t", T.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	/** 물으면 판정 원본 71 로 {@code event} 에 정해 둔 확률을 낸다 */
	private static Scripted answering(long event, double probability) {
		return new Scripted(
				query -> new MembershipJudgement.Judged(new EventJudgementId(71), Map.of(event, probability)));
	}

	private Map<String, @Nullable Object> eventRow(long event) {
		return this.jdbc.sql("""
				select status, merged_into, article_count,
				    to_char(closed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as closed,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as opened,
				    to_char(last_joined_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as joined
				from events where id = :event
				""").param("event", event).query().singleRow();
	}

	private @Nullable Object representativeColumn(long event) {
		return this.jdbc.sql("select representative_article_id from events where id = :event")
			.param("event", event)
			.query()
			.singleRow()
			.get("representative_article_id");
	}

	private float[] centroid(long event) {
		String text = this.jdbc.sql("select centroid::text from events where id = :event")
			.param("event", event)
			.query(String.class)
			.single();
		String[] parts = text.substring(1, text.length() - 1).split(",");
		float[] values = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Float.parseFloat(parts[i]);
		}
		return values;
	}

	private List<Long> members(long event) {
		return this.jdbc.sql("select article_id from event_articles where event_id = :event order by article_id")
			.param("event", event)
			.query(Long.class)
			.list();
	}

	private Map<String, Integer> termCounts(long event) {
		Map<String, Integer> counts = new HashMap<>();
		this.jdbc.sql("select term, article_count from event_key_terms where event_id = :event")
			.param("event", event)
			.query((java.sql.ResultSet rs) -> {
				counts.put(rs.getString("term"), rs.getInt("article_count"));
			});
		return counts;
	}

	private List<Map<String, Object>> judgementRows() {
		return this.jdbc.sql("""
				select event_low, event_high, probability, judgement_id from event_merge_judgements order by event_low
				""")
			.query()
			.listOfRows();
	}

	private List<Map<String, Object>> snapshot() {
		List<Map<String, Object>> rows = new ArrayList<>();
		rows.addAll(this.jdbc.sql("""
				select id, status, merged_into, article_count, centroid::text as centroid, revision,
				    merge_checked_revision, created_at, last_joined_at, closed_at
				from events order by id
				""").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_articles order by article_id").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_key_terms order by event_id, term").query().listOfRows());
		rows.addAll(this.jdbc.sql("select * from event_merge_judgements order by event_low").query().listOfRows());
		return rows;
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

	/** 알림마다 사건 ID 목록 — 발행 순(event-R23) */
	private List<List<Long>> changes() {
		return this.published.stream(EventsChanged.class)
			.map(changed -> changed.eventIds().stream().map(EventId::value).toList())
			.toList();
	}

}
