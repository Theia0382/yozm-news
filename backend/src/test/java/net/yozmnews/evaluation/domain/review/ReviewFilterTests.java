package net.yozmnews.evaluation.domain.review;

import static net.yozmnews.evaluation.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.region;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.evaluation.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import net.yozmnews.evaluation.EvaluationFilter;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TopicStatus;
import net.yozmnews.evaluation.domain.result.StoredTag;
import net.yozmnews.evaluation.domain.result.TagJudgement;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import org.junit.jupiter.api.Test;

/**
 * 평가 목록 거르기 — 고정 체계(ResultTagSet, 기준 0.5 / 0.1)의 판정으로 본다. 판정은 실제 {@link TagJudgement} 가 한다.
 * <p>
 * 지키는 규칙: evaluation-R31
 */
class ReviewFilterTests {

	/** 행정부 0.8(붙음) · 국회 0.4(저장됐지만 태그 기준 아래) · 국내 0.9 — 주제 판정 TAGGED */
	private static final Judged TAGGED = judge(List.of(topic(EXECUTIVE, 0.8), topic(ASSEMBLY, 0.4),
			region("domestic", 0.9)));

	/** 금융 0.3 하나 — 태그 기준 아래, 저장 기준 이상이라 그 하나로 LOW_CONFIDENCE */
	private static final Judged LOW = judge(List.of(topic(FINANCE, 0.3)));

	/** 저장된 주제 행 없음 — UNCLASSIFIED */
	private static final Judged NONE = judge(List.of(region("world", 0.7)));

	@Test
	void theJudgementsOfTheFixtureAreWhatTheTestsAssume() {
		assertThat(TAGGED.status()).isEqualTo(TopicStatus.TAGGED);
		assertThat(LOW.status()).isEqualTo(TopicStatus.LOW_CONFIDENCE);
		assertThat(NONE.status()).isEqualTo(TopicStatus.UNCLASSIFIED);
	}

	@Test
	void aTagNotInThatKindOfTheVersionIsUnknown() {
		assertThat(ReviewFilter.unknown(tag(TagKind.TOPIC, "politics"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(tag(TagKind.TOPIC, EXECUTIVE), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(tag(TagKind.REGION, "domestic"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(tag(TagKind.FORMAT, "opinion"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(EvaluationFilter.NONE, TAG_SET)).isEmpty();

		assertThat(ReviewFilter.unknown(tag(TagKind.TOPIC, "sports"), TAG_SET))
			.contains(new TagRef(TagKind.TOPIC, "sports"));
		// 다른 태그 종류의 키는 그 종류에 없다
		assertThat(ReviewFilter.unknown(tag(TagKind.TOPIC, "domestic"), TAG_SET))
			.contains(new TagRef(TagKind.TOPIC, "domestic"));
		assertThat(ReviewFilter.unknown(tag(TagKind.REGION, "politics"), TAG_SET))
			.contains(new TagRef(TagKind.REGION, "politics"));
		assertThat(ReviewFilter.unknown(tag(TagKind.FORMAT, "video"), TAG_SET))
			.contains(new TagRef(TagKind.FORMAT, "video"));
	}

	@Test
	void theStoredKeysAreTheNecessaryConditionOfTheTag() {
		// 대분류는 하위 중분류 전부
		assertThat(ReviewFilter.storedKeys(tag(TagKind.TOPIC, "politics"), TAG_SET))
			.contains(new StoredKeys(MultiTagKind.TOPIC, Set.of(EXECUTIVE, ASSEMBLY)));
		assertThat(ReviewFilter.storedKeys(tag(TagKind.TOPIC, FINANCE), TAG_SET))
			.contains(new StoredKeys(MultiTagKind.TOPIC, Set.of(FINANCE)));
		assertThat(ReviewFilter.storedKeys(tag(TagKind.REGION, "domestic"), TAG_SET))
			.contains(new StoredKeys(MultiTagKind.REGION, Set.of("domestic")));
		// 형식 · 주제 판정만은 저장 행 필요조건이 없다
		assertThat(ReviewFilter.storedKeys(tag(TagKind.FORMAT, "straight"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.storedKeys(new EvaluationFilter(null, TopicStatus.UNCLASSIFIED), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.storedKeys(EvaluationFilter.NONE, TAG_SET)).isEmpty();
		assertThatThrownBy(() -> ReviewFilter.storedKeys(tag(TagKind.TOPIC, "sports"), TAG_SET))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void onlyAFormatTagNarrowsByTheStoredRepresentativeFormat() {
		assertThat(ReviewFilter.formatKey(tag(TagKind.FORMAT, "opinion"), TAG_SET)).contains("opinion");
		assertThat(ReviewFilter.formatKey(tag(TagKind.TOPIC, "politics"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.formatKey(tag(TagKind.REGION, "domestic"), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.formatKey(new EvaluationFilter(null, TopicStatus.TAGGED), TAG_SET)).isEmpty();
		assertThatThrownBy(() -> ReviewFilter.formatKey(tag(TagKind.FORMAT, "video"), TAG_SET))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aTopicTagMatchesTheAttachedCategoryOrSubtopic() {
		assertThat(matches(tag(TagKind.TOPIC, "politics"), TAGGED, "straight")).isTrue();
		assertThat(matches(tag(TagKind.TOPIC, "economy"), TAGGED, "straight")).isFalse();
		assertThat(matches(tag(TagKind.TOPIC, EXECUTIVE), TAGGED, "straight")).isTrue();
		// 대조: 국회는 저장 행이 있지만(필요조건은 지난다) 태그 기준 아래라 붙지 않았다
		assertThat(matches(tag(TagKind.TOPIC, ASSEMBLY), TAGGED, "straight")).isFalse();
		// low_confidence 로 붙은 하나도 그 키 · 그 대분류 거르기에 나온다
		assertThat(matches(tag(TagKind.TOPIC, FINANCE), LOW, "straight")).isTrue();
		assertThat(matches(tag(TagKind.TOPIC, "economy"), LOW, "straight")).isTrue();
	}

	@Test
	void aRegionTagMatchesTheAttachedRegionAndAFormatTagTheRepresentativeFormat() {
		assertThat(matches(tag(TagKind.REGION, "domestic"), TAGGED, "straight")).isTrue();
		assertThat(matches(tag(TagKind.REGION, "world"), TAGGED, "straight")).isFalse();
		assertThat(matches(tag(TagKind.FORMAT, "straight"), TAGGED, "straight")).isTrue();
		assertThat(matches(tag(TagKind.FORMAT, "opinion"), TAGGED, "straight")).isFalse();
	}

	@Test
	void aTopicStatusMatchesThatStatusAndBothMustMatchTogether() {
		EvaluationFilter low = new EvaluationFilter(null, TopicStatus.LOW_CONFIDENCE);
		assertThat(matches(low, LOW, "straight")).isTrue();
		assertThat(matches(low, TAGGED, "straight")).isFalse();
		assertThat(matches(new EvaluationFilter(null, TopicStatus.UNCLASSIFIED), NONE, "straight")).isTrue();

		assertThat(matches(new EvaluationFilter(new TagRef(TagKind.TOPIC, "politics"), TopicStatus.TAGGED), TAGGED,
				"straight")).isTrue();
		assertThat(matches(new EvaluationFilter(new TagRef(TagKind.TOPIC, "politics"), TopicStatus.LOW_CONFIDENCE),
				TAGGED, "straight")).isFalse();
		assertThat(matches(new EvaluationFilter(new TagRef(TagKind.REGION, "world"), TopicStatus.TAGGED), TAGGED,
				"straight")).isFalse();
		assertThat(matches(EvaluationFilter.NONE, NONE, "feature")).isTrue();
	}

	private static boolean matches(EvaluationFilter filter, Judged judged, String formatKey) {
		return ReviewFilter.matches(filter, TAG_SET, judged, formatKey);
	}

	private static EvaluationFilter tag(TagKind kind, String key) {
		return new EvaluationFilter(new TagRef(kind, key), null);
	}

	private static Judged judge(List<StoredTag> stored) {
		return TagJudgement.judge(stored, TAG_SET, thresholds("0.5", "0.1", "0.5", "0.1"));
	}

}
