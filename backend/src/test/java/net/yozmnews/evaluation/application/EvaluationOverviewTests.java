package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluationAdministration;
import net.yozmnews.evaluation.EvaluationOverview;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.Share;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagShare;
import net.yozmnews.evaluation.TopicStatusCounts;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredTag;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import net.yozmnews.platform.ChangeActor;
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
 * 평가 현황 — 실제 PostgreSQL 에 넣은 평가 행을 결선된 체계 · 기준 · 이력 · 저장소로 센다. 기본 체계(버전 1, 처음 태그 기준 · 저장 기준
 * 0.5 / 0.1)를 쓰고, 테스트마다 평가 행 · 버전 · 기준을 처음으로 되돌린다.
 * <p>
 * 집계 트랜잭션은 DB 에 직접 묻는다 — 시험 설정이 태그 기준 · 저장 기준 저장소를 감싸, 지금 기준을 읽는 순간의 격리 수준 · 읽기 전용 여부를
 * {@code current_setting} 으로 적는다.
 * <p>
 * 지키는 규칙: evaluation-R29
 */
@Import({ TestcontainersConfiguration.class, TransactionSettings.class })
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EvaluationOverviewTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	@Autowired
	EvaluationAdministration administration;

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	TransactionSettings settings;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		TagBackfillRows.removeAll(this.jdbc);
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
		this.settings.seen.clear();
	}

	@BeforeEach
	void evaluateArticlesInAndAroundThePeriod() {
		reset();
		this.first = this.directory.activeTagSet().id();
		// 기간 안 둘: ① 행정부 0.8 · 국회 0.4(태그 기준 아래) · 국내 0.9 ② 금융 0.3(저장 기준 이상 · 태그 기준 아래 — low_confidence)
		insert(1, Instant.parse("2026-10-28T00:00:00Z"), 3.0, 1_000,
				List.of(topic("politics.executive", 0.8), topic("politics.legislature", 0.4),
						new StoredTag(MultiTagKind.REGION, "domestic", 0.9)));
		insert(2, Instant.parse("2026-11-01T00:00:00Z"), null, 1_500, List.of(topic("economy.finance", 0.3)));
		// 기간 밖 둘 — 시작 직전 · 끝
		insert(3, Instant.parse("2026-10-27T14:59:59Z"), 3.0, 2_000, List.of(topic("politics.executive", 0.9)));
		insert(4, Instant.parse("2026-11-03T15:00:00Z"), 3.0, 2_000, List.of(topic("politics.executive", 0.9)));
	}

	@Test
	void theEvaluationsOfTheVersionInThePeriodAreJudgedWithTheCurrentThresholds() {
		EvaluationOverview overview = this.administration.overview(this.first, PERIOD).orElseThrow();

		assertThat(overview.version()).isEqualTo(this.first);
		assertThat(overview.evaluations()).isEqualTo(2);
		assertThat(overview.topicStatus())
			.isEqualTo(new TopicStatusCounts(new Share(1, 0.5), new Share(1, 0.5), new Share(0, 0.0)));
		assertThat(overview.topics()).contains(new TagShare("politics.executive", "대통령실·행정부", new Share(1, 0.5)),
				new TagShare("politics.legislature", "국회·정당·선거", new Share(0, 0.0)),
				new TagShare("economy.finance", "금융", new Share(1, 0.5)));
		assertThat(overview.regions()).contains(new TagShare("domestic", "국내", new Share(1, 0.5)));
		assertThat(overview.importanceMissing()).isEqualTo(1);
		assertThat(overview.inputTokens().mean()).isEqualTo(1_250.0);
		assertThat(overview.topicProbabilities().storeThreshold()).isEqualByComparingTo("0.1");
		assertThat(overview.topicProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void changingTheThresholdsOfTheVersionChangesItsJudgementAtQueryTime() {
		this.tagSetAdministration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.3"), new BigDecimal("0.2"),
				ChangeActor.OPERATOR);

		EvaluationOverview overview = this.administration.overview(this.first, PERIOD).orElseThrow();

		// 태그 기준 0.3 이면 국회 0.4 · 금융 0.3 이 붙는다
		assertThat(overview.topicStatus().tagged()).isEqualTo(new Share(2, 1.0));
		assertThat(overview.topics()).contains(new TagShare("politics.legislature", "국회·정당·선거", new Share(1, 0.5)));
		assertThat(overview.topicProbabilities().storeThreshold()).isEqualByComparingTo("0.2");
		// 변경 시각(시험 실행 시각)이 기간 끝보다 뒤라 기간 안 평가는 한 바닥으로 저장됐다
		assertThat(overview.topicProbabilities().storeThresholdChanged()).isFalse();
		assertThat(overview.regionProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void aStoreThresholdChangedBeforeThePeriodLeavesThePeriodUniform() {
		TagSetId version = versionBeforeThePeriod();
		changeTopicStoreAt(version, "0.3", PERIOD.from().minus(Duration.ofHours(1)));

		EvaluationOverview overview = this.administration.overview(version, PERIOD).orElseThrow();

		assertThat(overview.topicProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void aStoreThresholdChangedWithinThePeriodIsFlagged() {
		TagSetId version = versionBeforeThePeriod();
		changeTopicStoreAt(version, "0.3", Instant.parse("2026-10-30T00:00:00Z"));

		EvaluationOverview overview = this.administration.overview(version, PERIOD).orElseThrow();

		assertThat(overview.topicProbabilities().storeThresholdChanged()).isTrue();
		assertThat(overview.regionProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void aFinishedBackfillOfThePeriodMakesItsFloorUniform() {
		TagSetId version = versionBeforeThePeriod();
		changeTopicStoreAt(version, "0.3", Instant.parse("2026-10-25T00:00:00Z"));
		changeTopicStoreAt(version, "0.1", Instant.parse("2026-10-30T00:00:00Z"));
		TagBackfillRows.insertFinished(this.jdbc, version, PERIOD.from(), PERIOD.to(), "0.1", 0);

		EvaluationOverview overview = this.administration.overview(version, PERIOD).orElseThrow();

		assertThat(overview.topicProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void theChosenVersionIsCountedEvenAfterAnotherVersionIsActivated() {
		TagSetId second = TagSetVersions.activate(
				this.tagSetAdministration.versionText(this.first).orElseThrow(), this.tagSets, this.thresholds,
				this.transaction, Instant.parse("2026-11-03T00:00:00Z"));

		assertThat(this.administration.overview(this.first, PERIOD).orElseThrow().evaluations()).isEqualTo(2);
		assertThat(this.administration.overview(second, PERIOD).orElseThrow().evaluations()).isZero();
	}

	@Test
	void anUnknownVersionGivesNoOverview() {
		assertThat(this.administration.overview(new TagSetId(999_999), PERIOD)).isEmpty();
	}

	@Test
	void theOverviewIsCountedInOneReadOnlyRepeatableReadTransaction() {
		// 대조: 집계 밖에서 읽으면 이 기록 수단이 기본 격리 수준 · 쓰기 가능을 적는다
		this.thresholds.current(this.first);
		this.administration.overview(this.first, PERIOD).orElseThrow();

		assertThat(this.settings.seen).containsExactly(Map.of("isolation", "read committed", "readOnly", "off"),
				Map.of("isolation", "repeatable read", "readOnly", "on"));
	}

	/** 기간 전(2026-10-20)에 처음 값을 둔 새 버전 — 첫 기동 적재의 처음 값 시각은 시험 실행 시각이라 기간보다 뒤다 */
	private TagSetId versionBeforeThePeriod() {
		return TagSetVersions.activate(this.tagSetAdministration.versionText(this.first).orElseThrow(), this.tagSets,
				this.thresholds, this.transaction, Instant.parse("2026-10-20T00:00:00Z"));
	}

	/** 그 버전 주제의 저장 기준을 그 시각에 바꾼다 — 시스템 시계를 쓰는 changeThresholds 로는 지난 시각의 변경을 만들 수 없다 */
	private void changeTopicStoreAt(TagSetId version, String store, Instant at) {
		this.transaction.executeWithoutResult(status -> {
			ThresholdValues current = this.thresholds.lockCurrent(version, MultiTagKind.TOPIC).orElseThrow();
			this.thresholds.apply(version, MultiTagKind.TOPIC, current,
					new ThresholdValues(current.tagThreshold(), new BigDecimal(store)), ChangeActor.OPERATOR, at);
		});
	}

	private void insert(long article, Instant at, @Nullable Double importance, int tokens, List<StoredTag> tags) {
		this.results.insert(new EvaluationRecord(new ArticleId(article), this.first, "fake", "fake-1", "straight", 0.7,
				Map.of("straight", 0.7, "opinion", 0.3), importance, Map.of(3, 1.0), tags, "{}", tokens, at));
	}

	private static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

}
