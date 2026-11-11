package net.yozmnews.evaluation.application;

import static net.yozmnews.evaluation.application.BackfillScenario.region;
import static net.yozmnews.evaluation.application.BackfillScenario.topic;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagBackfillRequestResult;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillState;
import net.yozmnews.evaluation.TagBackfillVersionView;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdWarning;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.StoreCoverage;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.platform.ChangeActor;
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
 * 빠진 태그 채움의 흐름 — 요청 → 회차 → 결과를 실제 PostgreSQL 로 본다. 다시 읽기의 결과(읽음 · 다른 평가기 · 읽을 수 없음)는
 * 대역({@link ScriptedTagging})의 대본이다 — 원본 응답 해석은 관문 시험이 본다. 버전 2 는 2026-10-01 에 처음 값(저장
 * 기준 0.1)으로 만들고 10-02 에 주제 저장 기준을 0.3 으로 올려 10-05 에 평가한 뒤 10-25 에 0.1 로 내렸다 — 그 평가에는 0.3 이상 주제만
 * 저장돼 있다. 버전 1 은 은퇴했고 저장 기준이 0.1 이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TagBackfillFlowTests {

	private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant EVALUATED = Instant.parse("2026-10-05T00:00:00Z");

	private static final Instant LOWERED = Instant.parse("2026-10-25T00:00:00Z");

	private static final Instant REQUESTED = Instant.parse("2026-10-31T00:00:00Z");

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	TagBackfillRepository backfills;

	@Autowired
	TagSetViews views;

	@Autowired
	TagSetVersioning versioning;

	@Autowired
	ThresholdPreviews previews;

	@Autowired
	StoreFloorsReader floors;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ArticleTagging tagging;

	@Autowired
	JdbcClient jdbc;

	private BackfillScenario scenario;

	private TagSetId first;

	private TagSetId second;

	@BeforeEach
	void aVersionWhoseStoreThresholdWasRaisedAndLowered() {
		this.scenario = new BackfillScenario(this.tagSetAdministration, this.tagSets, this.thresholds, this.results,
				this.transaction, this.jdbc);
		this.scenario.reset();
		this.first = this.tagSets.findByActiveTrue().orElseThrow().id();
		this.second = this.scenario.nextVersionAt(CREATED);
		this.scenario.changeTopicStoreAt(this.second, "0.3", Instant.parse("2026-10-02T00:00:00Z"));
	}

	@AfterEach
	void reset() {
		this.scenario.reset();
	}

	@Test
	void theStoredRawResponsesFillTheMissingTagsOfEveryVersionInTheRange() {
		this.scenario.insertRead(this.second, 7001, EVALUATED,
				Map.of("politics.executive", 0.45, "politics.legislature", 0.25, "politics.north-korea", 0.12,
						"economy.finance", 0.05),
				Map.of("domestic", 0.9), List.of(topic("politics.executive", 0.45), region("domestic", 0.9)));
		// 은퇴한 버전 1 의 평가 — 그 버전의 지금 저장 기준(0.1)까지 채운다
		this.scenario.insertRead(this.first, 7002, EVALUATED,
				Map.of("politics.executive", 0.45, "economy.finance", 0.2), Map.of(),
				List.of(topic("politics.executive", 0.45)));
		this.scenario.insertUnread(this.first, 7003, EVALUATED, TagReread.Unread.Reason.OTHER_EVALUATOR, List.of());
		this.scenario.insertUnread(this.first, 7004, EVALUATED, TagReread.Unread.Reason.UNREADABLE, List.of());
		// 범위(10-01 ~ 10-31) 밖 — 손대지 않는다
		this.scenario.insertRead(this.second, 7005, CREATED.minusSeconds(1), Map.of("politics.legislature", 0.25),
				Map.of(), List.of());
		this.scenario.changeTopicStoreAt(this.second, "0.1", LOWERED);

		TagBackfillRunView run = requestAndRun(REQUESTED, 30);

		assertThat(this.scenario.tags(7001)).containsExactly(region("domestic", 0.9), topic("politics.executive", 0.45),
				topic("politics.legislature", 0.25), topic("politics.north-korea", 0.12));
		assertThat(this.scenario.tags(7002)).containsExactly(topic("economy.finance", 0.2),
				topic("politics.executive", 0.45));
		assertThat(this.scenario.tags(7005)).isEmpty();
		assertThat(run.state()).isEqualTo(TagBackfillState.DONE);
		assertThat(run.versions()).containsExactly(
				new TagBackfillVersionView(this.first, 1, new BigDecimal("0.100"), new BigDecimal("0.100"), 3, 3, 1, 1, 1),
				new TagBackfillVersionView(this.second, 2, new BigDecimal("0.100"), new BigDecimal("0.100"), 1, 1, 2, 0,
						0));
		assertThat(List.of(run.processed(), run.tagsAdded(), run.skippedOtherEvaluator(), run.skippedUnreadable()))
			.containsExactly(4L, 3L, 1L, 1L);
		// 건너뛴 평가가 없는 버전 2 만 덮음이다
		assertThat(this.backfills.coverages(this.second, MultiTagKind.TOPIC))
			.containsExactly(new StoreCoverage(CREATED, REQUESTED, new BigDecimal("0.100")));
		assertThat(this.backfills.coverages(this.first, MultiTagKind.TOPIC)).isEmpty();
	}

	@Test
	void runningAgainAddsNothing() {
		this.scenario.insertRead(this.second, 7001, EVALUATED, Map.of("politics.legislature", 0.25), Map.of(),
				List.of());
		this.scenario.changeTopicStoreAt(this.second, "0.1", LOWERED);
		requestAndRun(REQUESTED, 30);

		TagBackfillRunView again = requestAndRun(REQUESTED.plus(Duration.ofHours(1)), 30);

		assertThat(again.processed()).isEqualTo(1);
		assertThat(again.tagsAdded()).isZero();
		assertThat(this.scenario.tags(7001)).containsExactly(topic("politics.legislature", 0.25));
	}

	@Test
	void afterTheBackfillTheTagThresholdBelowThePastStoreThresholdIsNotWarned() {
		this.scenario.insertRead(this.second, 7001, EVALUATED, Map.of("politics.legislature", 0.25), Map.of(),
				List.of());
		this.scenario.changeTopicStoreAt(this.second, "0.1", LOWERED);
		requestAndRun(REQUESTED, 30);

		assertThat(lowerTheTopicTagThresholdTo("0.2").warnings()).isEmpty();
	}

	@Test
	void aBackfillRequestedWithinTheReadLagOfTheLoweringLeavesTheWarning() {
		// 대조 — 내린 뒤 10분에 요청하면 "요청 시각 ~ 내린 시각 + 30분" 의 평가는 옛 기준(0.3)으로 저장됐을 수 있다
		this.scenario.insertRead(this.second, 7001, EVALUATED, Map.of("politics.legislature", 0.25), Map.of(),
				List.of());
		this.scenario.changeTopicStoreAt(this.second, "0.1", LOWERED);
		requestAndRun(LOWERED.plus(Duration.ofMinutes(10)), 30);

		assertThat(lowerTheTopicTagThresholdTo("0.2").warnings())
			.containsExactly(ThresholdWarning.TAG_BELOW_PAST_STORE);
	}

	/** 그 시각에 요청하고 시작 가능 시각 뒤에 회차를 돌린다 — 끝난 채움 */
	private TagBackfillRunView requestAndRun(Instant at, int days) {
		TagBackfillAdministrationService administration = new TagBackfillAdministrationService(this.tagSets,
				this.thresholds, this.results, this.backfills, this.transaction, InstantSource.fixed(at));
		TagBackfillRunView requested = ((TagBackfillRequestResult.Requested) administration.request(days)).run();
		new TagBackfillRound(this.backfills, this.results, this.thresholds, this.views,
				new ScriptedTagging(this.tagging).rereading(this.scenario::reread), this.transaction,
				InstantSource.fixed(at.plus(Duration.ofMinutes(2))))
			.run();
		return this.backfills.run(requested.id()).orElseThrow();
	}

	private ThresholdChangeResult.Changed lowerTheTopicTagThresholdTo(String tag) {
		TagSetAdministrationService administration = new TagSetAdministrationService(this.tagSets, this.thresholds,
				this.transaction, InstantSource.fixed(REQUESTED.plus(Duration.ofHours(2))), this.versioning,
				this.floors, this.previews);
		return (ThresholdChangeResult.Changed) administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal(tag),
				new BigDecimal("0.1"), ChangeActor.OPERATOR);
	}

}
