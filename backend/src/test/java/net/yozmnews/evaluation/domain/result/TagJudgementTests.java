package net.yozmnews.evaluation.domain.result;

import static net.yozmnews.evaluation.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.region;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TopicStatus;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import org.junit.jupiter.api.Test;

/**
 * 조회 시점 판정 — 붙음 · {@code low_confidence} · 미분류 · 대분류 펼치기 · 동률.
 * <p>
 * 지키는 규칙: evaluation-R15
 */
class TagJudgementTests {

	/** 태그 기준 0.5 · 저장 기준 0.1 (주제 · 지역 같음) */
	private static final TagSetThresholds DEFAULT = thresholds("0.5", "0.1", "0.5", "0.1");

	@Test
	void aTopicAtTheTagThresholdIsTaggedAndBringsItsCategory() {
		Judged judged = TagJudgement.judge(List.of(topic(FINANCE, 0.49), topic(ASSEMBLY, 0.5)), TAG_SET, DEFAULT);

		assertThat(judged).isEqualTo(new Judged(TopicStatus.TAGGED, List.of(new TagProbability(ASSEMBLY, 0.5)),
				List.of("politics"), List.of()));
	}

	@Test
	void taggedTopicsAndTheirCategoriesFollowTheTagSetOrder() {
		Judged judged = TagJudgement.judge(List.of(topic(FINANCE, 0.8), topic(ASSEMBLY, 0.6), topic(EXECUTIVE, 0.7)),
				TAG_SET, DEFAULT);

		assertThat(judged.topics()).containsExactly(new TagProbability(EXECUTIVE, 0.7),
				new TagProbability(ASSEMBLY, 0.6), new TagProbability(FINANCE, 0.8));
		assertThat(judged.topicCategories()).containsExactly("politics", "economy");
	}

	@Test
	void aTopicJustBelowTheTagThresholdIsLowConfidence() {
		Judged judged = TagJudgement.judge(List.of(topic(FINANCE, 0.49)), TAG_SET, DEFAULT);

		assertThat(judged).isEqualTo(new Judged(TopicStatus.LOW_CONFIDENCE, List.of(new TagProbability(FINANCE, 0.49)),
				List.of("economy"), List.of()));
	}

	@Test
	void theHighestStoredTopicIsLowConfidenceUntilTheStoreThresholdRisesAboveIt() {
		List<StoredTag> stored = List.of(topic(FINANCE, 0.2), topic(ASSEMBLY, 0.3));

		Judged before = TagJudgement.judge(stored, TAG_SET, DEFAULT);
		Judged raised = TagJudgement.judge(stored, TAG_SET, thresholds("0.5", "0.35", "0.5", "0.1"));

		assertThat(before).isEqualTo(new Judged(TopicStatus.LOW_CONFIDENCE, List.of(new TagProbability(ASSEMBLY, 0.3)),
				List.of("politics"), List.of()));
		assertThat(raised).isEqualTo(new Judged(TopicStatus.UNCLASSIFIED, List.of(), List.of(), List.of()));
	}

	@Test
	void noStoredTopicIsUnclassified() {
		Judged judged = TagJudgement.judge(List.of(region("world", 0.9)), TAG_SET, DEFAULT);

		assertThat(judged).isEqualTo(new Judged(TopicStatus.UNCLASSIFIED, List.of(), List.of(),
				List.of(new TagProbability("world", 0.9))));
	}

	@Test
	void aTieForTheHighestTopicGoesToTheEarlierTagInTheTagSet() {
		// executive 는 체계 순서가 앞서고 키의 사전 순은 뒤다. 넘기는 순서도 뒤집는다
		Judged judged = TagJudgement.judge(List.of(topic(ASSEMBLY, 0.3), topic(EXECUTIVE, 0.3)), TAG_SET, DEFAULT);

		assertThat(judged.topics()).containsExactly(new TagProbability(EXECUTIVE, 0.3));
	}

	@Test
	void regionsAreAttachedOnlyAtTheTagThresholdWithoutALowConfidenceFallback() {
		Judged attached = TagJudgement.judge(List.of(region("domestic", 0.2), region("world", 0.5)), TAG_SET, DEFAULT);
		Judged none = TagJudgement.judge(List.of(region("domestic", 0.2)), TAG_SET, DEFAULT);

		assertThat(attached.regions()).containsExactly(new TagProbability("world", 0.5));
		assertThat(none.regions()).isEmpty();
	}

	@Test
	void eachTagKindIsJudgedByItsOwnThresholds() {
		// 주제 태그 기준 0.5 · 저장 기준 0.1, 지역 태그 기준 0.3 · 저장 기준 0.3 — 종류를 바꿔 쓰면 주제는 붙고 지역은 떨어진다
		TagSetThresholds split = thresholds("0.5", "0.1", "0.3", "0.3");
		Judged judged = TagJudgement.judge(List.of(topic(ASSEMBLY, 0.25), region("domestic", 0.375)), TAG_SET, split);

		assertThat(judged).isEqualTo(new Judged(TopicStatus.LOW_CONFIDENCE, List.of(new TagProbability(ASSEMBLY, 0.25)),
				List.of("politics"), List.of(new TagProbability("domestic", 0.375))));
	}

	@Test
	void rejectsThresholdsOfAnotherVersionAndATagOutsideTheTagSet() {
		TagSetThresholds other = new TagSetThresholds(new TagSetId(2), DEFAULT.topic(), DEFAULT.region());

		assertThatIllegalArgumentException().isThrownBy(() -> TagJudgement.judge(List.of(), TAG_SET, other));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TagJudgement.judge(List.of(topic("politics.unknown", 0.9)), TAG_SET, DEFAULT));
	}

}
