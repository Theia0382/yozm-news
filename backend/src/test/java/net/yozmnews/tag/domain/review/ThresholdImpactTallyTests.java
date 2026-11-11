package net.yozmnews.tag.domain.review;

import static net.yozmnews.tag.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.tag.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.tag.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.tag.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.tag.domain.result.ResultTagSet.VERSION;
import static net.yozmnews.tag.domain.result.ResultTagSet.region;
import static net.yozmnews.tag.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.tag.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.domain.overview.EvaluationSample;
import net.yozmnews.tag.domain.result.StoredEvaluation;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.threshold.StoreFloors;
import org.junit.jupiter.api.Test;

/**
 * 기준 미리보기 집계 — 고정 체계(ResultTagSet)의 평가 넷을 지금 기준(0.5 / 0.1)과 제안 기준으로 센다. 기대값은 판정 규칙(tag-R15)을
 * 손으로 적용한 수다.
 * <p>
 * ① 행정부 0.45 — 지금 low_confidence ② 행정부 0.8 — 지금 TAGGED ③ 국회 0.45 · 금융 0.42 — 지금 국회 하나로 low_confidence ④ 행정부 0.8 ·
 * 국외 0.45 — 지금 TAGGED, 지역 없음.
 * <p>
 * 지키는 규칙: tag-R35
 */
class ThresholdImpactTallyTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-11-01T00:00:00Z"),
			Instant.parse("2026-11-02T00:00:00Z"));

	/** 기간 전 처음 값 그대로인 저장 바닥 — 두 집계가 함께 쓴다 */
	private static final StoreFloors UNIFORM_TOPIC = floors(MultiTagKind.TOPIC);

	private static final StoreFloors UNIFORM_REGION = floors(MultiTagKind.REGION);

	private static final TagSetThresholds CURRENT = thresholds("0.5", "0.1", "0.5", "0.1");

	private static final List<List<StoredTag>> EVALUATIONS = List.of(List.of(topic(EXECUTIVE, 0.45)),
			List.of(topic(EXECUTIVE, 0.8)), List.of(topic(ASSEMBLY, 0.45), topic(FINANCE, 0.42)),
			List.of(topic(EXECUTIVE, 0.8), region("world", 0.45)));

	@Test
	void loweringTheTopicTagThresholdCountsTheEvaluationsWhoseTopicJudgementChanges() {
		ThresholdImpactTally tally = tally(thresholds("0.4", "0.1", "0.5", "0.1"), MultiTagKind.TOPIC);

		// ① low_confidence → TAGGED(같은 키), ③ 국회 하나 → 국회 · 금융. ② · ④ 는 그대로
		assertThat(tally.changed()).isEqualTo(2);
		EvaluationOverview before = tally.before();
		EvaluationOverview after = tally.after();
		assertThat(before.evaluations()).isEqualTo(4);
		assertThat(before.topicStatus().tagged().count()).isEqualTo(2);
		assertThat(before.topicStatus().lowConfidence().count()).isEqualTo(2);
		assertThat(after.topicStatus().tagged().count()).isEqualTo(4);
		assertThat(after.topicStatus().lowConfidence().count()).isZero();
	}

	@Test
	void aTopicJudgementThatKeepsItsStatusButAttachesAnotherSubtopicChanges() {
		ThresholdImpactTally tally = new ThresholdImpactTally(TAG_SET, CURRENT, thresholds("0.4", "0.1", "0.5", "0.1"),
				MultiTagKind.TOPIC, UNIFORM_TOPIC, UNIFORM_REGION, PERIOD);
		// 행정부 0.8 · 국회 0.45 — 두 기준 모두 TAGGED 이고, 제안 기준에서 국회가 더 붙는다
		tally.add(new EvaluationSample(new StoredEvaluation(new ArticleId(5), VERSION, "straight", 3.0,
				List.of(topic(EXECUTIVE, 0.8), topic(ASSEMBLY, 0.45))), 0.7, 100));

		assertThat(tally.before().topicStatus().tagged().count()).isEqualTo(1);
		assertThat(tally.after().topicStatus().tagged().count()).isEqualTo(1);
		assertThat(tally.changed()).isEqualTo(1);
	}

	@Test
	void raisingTheTopicStoreThresholdTurnsLowConfidenceBelowItIntoUnclassified() {
		ThresholdImpactTally tally = tally(thresholds("0.5", "0.5", "0.5", "0.1"), MultiTagKind.TOPIC);

		// ① · ③ 의 최고 확률 0.45 가 새 저장 기준 아래 — 미분류
		assertThat(tally.changed()).isEqualTo(2);
		assertThat(tally.after().topicStatus().unclassified().count()).isEqualTo(2);
		// 미리보기 쪽 확률 분포의 저장 범위는 제안 저장 기준으로 정한다
		assertThat(tally.before().topicProbabilities().storeThreshold()).isEqualByComparingTo("0.1");
		assertThat(tally.after().topicProbabilities().storeThreshold()).isEqualByComparingTo("0.5");
	}

	@Test
	void aRegionChangeCountsOnlyTheRegionJudgement() {
		ThresholdImpactTally tally = tally(thresholds("0.5", "0.1", "0.4", "0.1"), MultiTagKind.REGION);

		// ④ 에 국외가 붙는다. 대조: 주제 판정이 지금 low_confidence 인 ① · ③ 은 지역 변경에서 세지 않는다
		assertThat(tally.changed()).isEqualTo(1);
		assertThat(tally.after().regions()).filteredOn(share -> share.key().equals("world"))
			.singleElement()
			.satisfies(share -> assertThat(share.share().count()).isEqualTo(1));
	}

	@Test
	void theSameThresholdsChangeNothing() {
		assertThat(tally(CURRENT, MultiTagKind.TOPIC).changed()).isZero();
	}

	private static ThresholdImpactTally tally(TagSetThresholds proposed, MultiTagKind kind) {
		ThresholdImpactTally tally = new ThresholdImpactTally(TAG_SET, CURRENT, proposed, kind, UNIFORM_TOPIC, UNIFORM_REGION,
				PERIOD);
		long article = 1;
		for (List<StoredTag> tags : EVALUATIONS) {
			StoredEvaluation evaluation = new StoredEvaluation(new ArticleId(article++), VERSION, "straight", 3.0, tags);
			tally.add(new EvaluationSample(evaluation, 0.7, 100));
		}
		return tally;
	}

	private static StoreFloors floors(MultiTagKind kind) {
		return StoreFloors.of(kind, List.of(new ThresholdChangeView(kind, null, null, new BigDecimal("0.5"),
				new BigDecimal("0.1"), ChangeActor.SYSTEM, Instant.parse("2026-10-01T00:00:00Z"))), List.of());
	}

}
