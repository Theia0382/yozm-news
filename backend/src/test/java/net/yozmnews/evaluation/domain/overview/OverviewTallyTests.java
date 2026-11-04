package net.yozmnews.evaluation.domain.overview;

import static net.yozmnews.evaluation.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.VERSION;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.region;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.CountBucket;
import net.yozmnews.evaluation.EvaluationOverview;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.ProbabilityBucket;
import net.yozmnews.evaluation.Share;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TagShare;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.TokenBucket;
import net.yozmnews.evaluation.TopicStatusCounts;
import net.yozmnews.evaluation.ValueBucket;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.StoredTag;
import net.yozmnews.evaluation.domain.threshold.StoreFloors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 평가 현황 집계 — 고정 체계(버전 1)와 태그 기준 · 저장 기준 0.5 / 0.1 에서 평가 넷의 판정 · 태그 분포 · 태그 수 · 확률 · 중요도 · 입력
 * 토큰을 수기 리터럴로 본다.
 * <p>
 * 평가 넷: ① 행정부 0.9 · 국회 0.6 · 금융 0.2(태그 기준 아래), 국제 0.8 · 국내 0.7, 스트레이트 0.85, 중요도 4.2, 1,200 토큰 ② 금융 0.7,
 * 국내 0.3(태그 기준 아래), 오피니언 0.6, 중요도 1.0, 1,500 토큰 ③ 행정부 0.3(저장 기준 이상 · 태그 기준 아래 — low_confidence), 스트레이트
 * 0.4, 중요도 없음, 900 토큰 ④ 주제 없음(미분류), 국제 0.55, 기획 0.95, 중요도 5.0, 2,100 토큰.
 * <p>
 * 지키는 규칙: evaluation-R29
 */
class OverviewTallyTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	/** 기간 전 처음 값 그대로인 저장 바닥 — 기간 안 바닥이 하나다 */
	private static final StoreFloors UNIFORM_TOPIC = StoreFloors.of(MultiTagKind.TOPIC,
			List.of(initial(MultiTagKind.TOPIC)), List.of());

	private static final StoreFloors UNIFORM_REGION = StoreFloors.of(MultiTagKind.REGION,
			List.of(initial(MultiTagKind.REGION)), List.of());

	@Test
	void fourEvaluationsAreJudgedAndCountedWithTheCurrentThresholds() {
		EvaluationOverview overview = fourEvaluations();

		assertThat(overview.version()).isEqualTo(VERSION);
		assertThat(overview.from()).isEqualTo(Instant.parse("2026-10-27T15:00:00Z"));
		assertThat(overview.to()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
		assertThat(overview.evaluations()).isEqualTo(4);
		assertThat(overview.topicStatus()).isEqualTo(
				new TopicStatusCounts(new Share(2, 0.5), new Share(1, 0.25), new Share(1, 0.25)));
	}

	@Test
	void everyTagOfTheVersionIsListedInTagSetOrderWithWhatTheJudgementAttached() {
		EvaluationOverview overview = fourEvaluations();

		// low_confidence 의 하나(③ 행정부)도 붙은 주제다. ① 의 금융 0.2 는 저장됐지만 붙지 않았다
		assertThat(overview.topics()).containsExactly(new TagShare(EXECUTIVE, "대통령실·행정부", new Share(2, 0.5)),
				new TagShare(ASSEMBLY, "국회·정당·선거", new Share(1, 0.25)),
				new TagShare(FINANCE, "금융", new Share(1, 0.25)));
		assertThat(overview.topicCategories()).containsExactly(new TagShare("politics", "정치", new Share(2, 0.5)),
				new TagShare("economy", "경제", new Share(1, 0.25)));
		assertThat(overview.regions()).containsExactly(new TagShare("world", "국제", new Share(2, 0.5)),
				new TagShare("domestic", "국내", new Share(1, 0.25)));
		assertThat(overview.formats()).containsExactly(new TagShare("straight", "스트레이트", new Share(2, 0.5)),
				new TagShare("opinion", "오피니언", new Share(1, 0.25)), new TagShare("feature", "기획", new Share(1, 0.25)));
	}

	@Test
	void attachedTagsPerArticleRunFromZeroToFiveOrMore() {
		EvaluationOverview overview = fourEvaluations();

		// 주제의 0 은 미분류(④), low_confidence(③)는 1
		assertThat(overview.topicsPerArticle()).containsExactly(new CountBucket(0, false, new Share(1, 0.25)),
				new CountBucket(1, false, new Share(2, 0.5)), new CountBucket(2, false, new Share(1, 0.25)),
				new CountBucket(3, false, new Share(0, 0.0)), new CountBucket(4, false, new Share(0, 0.0)),
				new CountBucket(5, true, new Share(0, 0.0)));
		assertThat(overview.regionsPerArticle().stream().map(bucket -> bucket.share().count())).containsExactly(2L, 1L,
				1L, 0L, 0L, 0L);
	}

	@Test
	void fiveOrMoreAttachedTagsFallInTheLastBucket() {
		List<AskedTag> regions = List.of(new AskedTag("r1", "지역1", "지역1", "지역1인가"),
				new AskedTag("r2", "지역2", "지역2", "지역2인가"), new AskedTag("r3", "지역3", "지역3", "지역3인가"),
				new AskedTag("r4", "지역4", "지역4", "지역4인가"), new AskedTag("r5", "지역5", "지역5", "지역5인가"),
				new AskedTag("r6", "지역6", "지역6", "지역6인가"));
		TagSetView tagSet = new TagSetView(VERSION, 1, TAG_SET.topics(), regions, TAG_SET.format(), TAG_SET.importance());
		OverviewTally tally = new OverviewTally(tagSet, thresholds("0.5", "0.1", "0.5", "0.1"), UNIFORM_TOPIC,
				UNIFORM_REGION, PERIOD);
		// 붙은 지역 여섯 · 다섯 — 둘 다 "5 이상" 칸
		tally.add(sample(List.of(region("r1", 0.9), region("r2", 0.9), region("r3", 0.9), region("r4", 0.9),
				region("r5", 0.9), region("r6", 0.9)), "straight", 0.9, 3.0, 1_000));
		tally.add(sample(List.of(region("r1", 0.9), region("r2", 0.9), region("r3", 0.9), region("r4", 0.9),
				region("r5", 0.9)), "straight", 0.9, 3.0, 1_000));
		tally.add(sample(List.of(region("r1", 0.9), region("r2", 0.9), region("r3", 0.9), region("r4", 0.9)),
				"straight", 0.9, 3.0, 1_000));

		assertThat(tally.overview().regionsPerArticle()).containsExactly(new CountBucket(0, false, new Share(0, 0.0)),
				new CountBucket(1, false, new Share(0, 0.0)), new CountBucket(2, false, new Share(0, 0.0)),
				new CountBucket(3, false, new Share(0, 0.0)), new CountBucket(4, false, new Share(1, 0.3333333333333333)),
				new CountBucket(5, true, new Share(2, 0.6666666666666666)));
	}

	@Test
	void probabilitiesAreEveryStoredTagRowAndTheRepresentativeFormat() {
		EvaluationOverview overview = fourEvaluations();

		// 주제 저장 행 0.9 · 0.6 · 0.2 · 0.7 · 0.3 — 붙었는지와 무관하다
		assertThat(counts(overview.topicProbabilities().buckets())).containsExactly(0, 0, 1, 1, 0, 0, 1, 1, 0, 1);
		assertThat(overview.topicProbabilities().storeThreshold()).isEqualByComparingTo("0.1");
		assertThat(overview.topicProbabilities().storeThresholdChanged()).isFalse();
		// 지역 저장 행 0.8 · 0.7 · 0.3 · 0.55
		assertThat(counts(overview.regionProbabilities().buckets())).containsExactly(0, 0, 0, 1, 0, 1, 0, 1, 1, 0);
		// 대표 형식 0.85 · 0.6 · 0.4 · 0.95 — 저장 기준이 없다
		assertThat(counts(overview.formatProbabilities().buckets())).containsExactly(0, 0, 0, 0, 1, 0, 1, 0, 1, 1);
		assertThat(overview.formatProbabilities().storeThreshold()).isNull();
	}

	@Test
	void importanceIsTheExpectedValueBucketAndMissingValuesAreCountedApart() {
		EvaluationOverview overview = fourEvaluations();

		assertThat(overview.importance()).hasSize(8);
		assertThat(overview.importance().getFirst())
			.isEqualTo(new ValueBucket(new BigDecimal("1.0"), new BigDecimal("1.5"), false, new Share(1, 0.25)));
		assertThat(overview.importance().get(6))
			.isEqualTo(new ValueBucket(new BigDecimal("4.0"), new BigDecimal("4.5"), false, new Share(1, 0.25)));
		assertThat(overview.importance().getLast())
			.isEqualTo(new ValueBucket(new BigDecimal("4.5"), new BigDecimal("5.0"), true, new Share(1, 0.25)));
		assertThat(overview.importanceMissing()).isEqualTo(1);
	}

	@Test
	void inputTokensHaveMeanMinMaxAndBuckets() {
		EvaluationOverview overview = fourEvaluations();

		assertThat(overview.inputTokens().mean()).isEqualTo(1_425.0);
		assertThat(overview.inputTokens().min()).isEqualTo(900);
		assertThat(overview.inputTokens().max()).isEqualTo(2_100);
		assertThat(overview.inputTokens().buckets()).startsWith(new TokenBucket(750, 1_000, 1)).hasSize(6);
	}

	@Test
	void raisingTheTagThresholdChangesTheJudgementAtQueryTime() {
		OverviewTally tally = new OverviewTally(TAG_SET, thresholds("0.95", "0.1", "0.5", "0.1"), UNIFORM_TOPIC,
				UNIFORM_REGION, PERIOD);
		tally.add(sample(List.of(topic(EXECUTIVE, 0.9), topic(ASSEMBLY, 0.6)), "straight", 0.9, 3.0, 1_000));

		// 태그 기준 0.95 면 저장 기준 이상인 가장 높은 행 하나(행정부 0.9)로 low_confidence
		EvaluationOverview overview = tally.overview();
		assertThat(overview.topicStatus().lowConfidence()).isEqualTo(new Share(1, 1.0));
		assertThat(overview.topics().getFirst().share()).isEqualTo(new Share(1, 1.0));
		assertThat(overview.topics().get(1).share()).isEqualTo(new Share(0, 0.0));
	}

	@Test
	void noEvaluationsGiveZeroCountsAndNoRatios() {
		EvaluationOverview overview = new OverviewTally(TAG_SET, thresholds("0.5", "0.1", "0.5", "0.1"), UNIFORM_TOPIC,
				UNIFORM_REGION, PERIOD)
			.overview();

		assertThat(overview.evaluations()).isZero();
		assertThat(overview.topicStatus())
			.isEqualTo(new TopicStatusCounts(new Share(0, null), new Share(0, null), new Share(0, null)));
		assertThat(overview.topics()).hasSize(3).allMatch(share -> share.share().equals(new Share(0, null)));
		assertThat(overview.topicsPerArticle()).hasSize(6);
		assertThat(overview.topicProbabilities().buckets()).hasSize(10).allMatch(bucket -> bucket.count() == 0);
		assertThat(overview.importance()).hasSize(8).allMatch(bucket -> bucket.share().equals(new Share(0, null)));
		assertThat(overview.importanceMissing()).isZero();
		assertThat(overview.inputTokens().mean()).isNull();
		assertThat(overview.inputTokens().min()).isNull();
		assertThat(overview.inputTokens().max()).isNull();
		assertThat(overview.inputTokens().buckets()).isEmpty();
	}

	@Test
	void aMixedStoreFloorWithinThePeriodIsFlaggedPerTagKind() {
		// 주제는 기간 안(2026-11-01)에 저장 기준을 바꿨고, 지역은 처음 값 그대로다
		StoreFloors topic = StoreFloors.of(MultiTagKind.TOPIC, List.of(
				new ThresholdChangeView(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.1"),
						new BigDecimal("0.5"), new BigDecimal("0.2"), ChangeActor.OPERATOR,
						Instant.parse("2026-11-01T00:00:00Z")),
				initial(MultiTagKind.TOPIC)), List.of());

		EvaluationOverview overview = new OverviewTally(TAG_SET, thresholds("0.5", "0.2", "0.6", "0.1"), topic,
				UNIFORM_REGION, PERIOD)
			.overview();

		assertThat(overview.topicProbabilities().storeThresholdChanged()).isTrue();
		assertThat(overview.regionProbabilities().storeThresholdChanged()).isFalse();
		assertThat(overview.formatProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void anEvaluationOfAnotherVersionOrWithAnUnknownFormatIsACallerDefect() {
		OverviewTally tally = new OverviewTally(TAG_SET, thresholds("0.5", "0.1", "0.5", "0.1"), UNIFORM_TOPIC,
				UNIFORM_REGION, PERIOD);

		assertThatIllegalArgumentException().isThrownBy(() -> tally.add(new EvaluationSample(
				new StoredEvaluation(new ArticleId(1), new TagSetId(2), "straight", 3.0, List.of()), 0.9, 100)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> tally.add(sample(List.of(), "unknown", 0.9, 3.0, 100)));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new OverviewTally(TAG_SET, new net.yozmnews.evaluation.TagSetThresholds(new TagSetId(2),
						thresholds("0.5", "0.1", "0.5", "0.1").topic(), thresholds("0.5", "0.1", "0.5", "0.1").region()),
						UNIFORM_TOPIC, UNIFORM_REGION, PERIOD));
	}

	private static EvaluationOverview fourEvaluations() {
		OverviewTally tally = new OverviewTally(TAG_SET, thresholds("0.5", "0.1", "0.5", "0.1"), UNIFORM_TOPIC,
				UNIFORM_REGION, PERIOD);
		tally.add(sample(List.of(topic(EXECUTIVE, 0.9), topic(ASSEMBLY, 0.6), topic(FINANCE, 0.2),
				region("world", 0.8), region("domestic", 0.7)), "straight", 0.85, 4.2, 1_200));
		tally.add(sample(List.of(topic(FINANCE, 0.7), region("domestic", 0.3)), "opinion", 0.6, 1.0, 1_500));
		tally.add(sample(List.of(topic(EXECUTIVE, 0.3)), "straight", 0.4, null, 900));
		tally.add(sample(List.of(region("world", 0.55)), "feature", 0.95, 5.0, 2_100));
		return tally.overview();
	}

	private static ThresholdChangeView initial(MultiTagKind kind) {
		return new ThresholdChangeView(kind, null, null, new BigDecimal("0.5"), new BigDecimal("0.1"), ChangeActor.SYSTEM,
				Instant.parse("2026-10-01T00:00:00Z"));
	}

	private static EvaluationSample sample(List<StoredTag> tags, String format, double formatProbability,
			@Nullable Double importance, int tokens) {
		return new EvaluationSample(new StoredEvaluation(new ArticleId(1), VERSION, format, importance, tags),
				formatProbability, tokens);
	}

	private static long[] counts(List<ProbabilityBucket> buckets) {
		return buckets.stream().mapToLong(ProbabilityBucket::count).toArray();
	}

}
