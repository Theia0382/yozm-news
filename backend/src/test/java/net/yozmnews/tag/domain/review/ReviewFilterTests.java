package net.yozmnews.tag.domain.review;

import static net.yozmnews.tag.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.tag.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.tag.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.tag.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.tag.domain.result.ResultTagSet.region;
import static net.yozmnews.tag.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.tag.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import net.yozmnews.tag.EvaluationFilter;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.result.TagJudgement;
import net.yozmnews.tag.domain.result.TagJudgement.Judged;
import org.junit.jupiter.api.Test;

/**
 * 평가 목록 거르기 — 고정 체계(ResultTagSet, 기준 0.5 / 0.1)의 판정으로 본다. 판정은 실제 {@link TagJudgement} 가 한다.
 * <p>
 * 지키는 규칙: tag-R33
 */
class ReviewFilterTests {

	/** 행정부 0.8(붙음) · 국회 0.4(저장됐지만 태그 기준 아래) · 국내 0.9 — 주제 판정 TAGGED */
	private static final Judged TAGGED = judge(List.of(topic(EXECUTIVE, 0.8), topic(ASSEMBLY, 0.4),
			region("domestic", 0.9)));

	/** 행정부 0.8 · 국회 0.6 · 국제 0.7 · 국내 0.6 — 같은 종류의 태그가 둘씩 붙었다, TAGGED */
	private static final Judged PAIRS = judge(List.of(topic(EXECUTIVE, 0.8), topic(ASSEMBLY, 0.6),
			region("world", 0.7), region("domestic", 0.6)));

	/** 금융 0.3 하나 — 태그 기준 아래, 저장 기준 이상이라 그 하나로 LOW_CONFIDENCE */
	private static final Judged LOW = judge(List.of(topic(FINANCE, 0.3)));

	/** 저장된 주제 행 없음 — UNCLASSIFIED */
	private static final Judged NONE = judge(List.of(region("world", 0.7)));

	@Test
	void theJudgementsOfTheFixtureAreWhatTheTestsAssume() {
		assertThat(TAGGED.status()).isEqualTo(TopicStatus.TAGGED);
		assertThat(TAGGED.topics()).hasSize(1);
		assertThat(PAIRS.status()).isEqualTo(TopicStatus.TAGGED);
		assertThat(PAIRS.topics()).hasSize(2);
		assertThat(PAIRS.regions()).hasSize(2);
		assertThat(LOW.status()).isEqualTo(TopicStatus.LOW_CONFIDENCE);
		assertThat(NONE.status()).isEqualTo(TopicStatus.UNCLASSIFIED);
	}

	@Test
	void theFilterKeepsTheChosenOrderAndTheSameTagOnce() {
		EvaluationFilter filter = filter(regionTag("domestic"), topicTag("politics"), regionTag("domestic"));

		assertThat(filter.tags()).containsExactly(regionTag("domestic"), topicTag("politics"));
		assertThat(EvaluationFilter.NONE.tags()).isEmpty();
	}

	@Test
	void theTagsNotInThatKindOfTheVersionAreUnknownInTheChosenOrder() {
		assertThat(ReviewFilter.unknown(filter(topicTag("politics")), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(filter(topicTag(EXECUTIVE)), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(filter(regionTag("domestic")), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(filter(formatTag("opinion")), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.unknown(EvaluationFilter.NONE, TAG_SET)).isEmpty();

		assertThat(ReviewFilter.unknown(filter(topicTag("sports")), TAG_SET)).containsExactly(topicTag("sports"));
		assertThat(ReviewFilter.unknown(filter(formatTag("video")), TAG_SET)).containsExactly(formatTag("video"));
		// 다른 태그 종류의 키는 그 종류에 없다. 있는 태그는 빠지고 없는 태그는 모두 고른 순서로 나온다
		assertThat(ReviewFilter.unknown(
				filter(regionTag("politics"), topicTag("politics"), topicTag("domestic"), regionTag("domestic")),
				TAG_SET))
			.containsExactly(regionTag("politics"), topicTag("domestic"));
	}

	@Test
	void twoFormatTagsAreMultipleFormats() {
		assertThat(ReviewFilter.multipleFormats(filter(formatTag("straight"), formatTag("opinion")))).isTrue();
		// 없는 형식 키도 형식 태그로 센다
		assertThat(ReviewFilter.multipleFormats(filter(formatTag("straight"), formatTag("video")))).isTrue();

		assertThat(ReviewFilter.multipleFormats(filter(formatTag("straight"), topicTag("politics"),
				regionTag("domestic")))).isFalse();
		// 같은 형식 태그는 한 번으로 센다
		assertThat(ReviewFilter.multipleFormats(filter(formatTag("straight"), formatTag("straight")))).isFalse();
		assertThat(ReviewFilter.multipleFormats(EvaluationFilter.NONE)).isFalse();
	}

	@Test
	void eachTopicOrRegionTagHasItsOwnNecessaryCondition() {
		// 대분류는 하위 중분류 전부, 중분류 · 지역은 그 키 — 고른 순서로 태그마다 하나
		assertThat(ReviewFilter.storedKeys(filter(topicTag("politics"), formatTag("straight"),
				regionTag("domestic"), topicTag(FINANCE)), TAG_SET))
			.containsExactly(new StoredKeys(MultiTagKind.TOPIC, Set.of(EXECUTIVE, ASSEMBLY)),
					new StoredKeys(MultiTagKind.REGION, Set.of("domestic")),
					new StoredKeys(MultiTagKind.TOPIC, Set.of(FINANCE)));
		// 형식 · 주제 판정만은 저장 행 필요조건이 없다
		assertThat(ReviewFilter.storedKeys(filter(formatTag("straight")), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.storedKeys(new EvaluationFilter(List.of(), TopicStatus.UNCLASSIFIED), TAG_SET))
			.isEmpty();
		assertThat(ReviewFilter.storedKeys(EvaluationFilter.NONE, TAG_SET)).isEmpty();
	}

	@Test
	void onlyAFormatTagNarrowsByTheStoredRepresentativeFormat() {
		assertThat(ReviewFilter.formatKey(filter(topicTag("politics"), formatTag("opinion")), TAG_SET))
			.contains("opinion");
		assertThat(ReviewFilter.formatKey(filter(topicTag("politics"), regionTag("domestic")), TAG_SET)).isEmpty();
		assertThat(ReviewFilter.formatKey(new EvaluationFilter(List.of(), TopicStatus.TAGGED), TAG_SET)).isEmpty();
	}

	@Test
	void aFilterThatDidNotPassTheChecksIsRefused() {
		EvaluationFilter unknown = filter(topicTag("politics"), topicTag("sports"));
		EvaluationFilter formats = filter(formatTag("straight"), formatTag("opinion"));

		for (EvaluationFilter filter : List.of(unknown, formats)) {
			assertThatThrownBy(() -> ReviewFilter.storedKeys(filter, TAG_SET))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ReviewFilter.formatKey(filter, TAG_SET))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ReviewFilter.matches(filter, TAG_SET, TAGGED, "straight"))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void aTopicTagMatchesTheAttachedCategoryOrSubtopic() {
		assertThat(matches(filter(topicTag("politics")), TAGGED, "straight")).isTrue();
		assertThat(matches(filter(topicTag("economy")), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(topicTag(EXECUTIVE)), TAGGED, "straight")).isTrue();
		// 대조: 국회는 저장 행이 있지만(필요조건은 지난다) 태그 기준 아래라 붙지 않았다
		assertThat(matches(filter(topicTag(ASSEMBLY)), TAGGED, "straight")).isFalse();
		// low_confidence 로 붙은 하나도 그 키 · 그 대분류 거르기에 나온다
		assertThat(matches(filter(topicTag(FINANCE)), LOW, "straight")).isTrue();
		assertThat(matches(filter(topicTag("economy")), LOW, "straight")).isTrue();
	}

	@Test
	void aRegionTagMatchesTheAttachedRegionAndAFormatTagTheRepresentativeFormat() {
		assertThat(matches(filter(regionTag("domestic")), TAGGED, "straight")).isTrue();
		assertThat(matches(filter(regionTag("world")), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(formatTag("straight")), TAGGED, "straight")).isTrue();
		assertThat(matches(filter(formatTag("opinion")), TAGGED, "straight")).isFalse();
	}

	@Test
	void everyChosenTagMustMatchAcrossKinds() {
		assertThat(matches(filter(topicTag("politics"), regionTag("domestic"), formatTag("straight")), TAGGED,
				"straight")).isTrue();
		// 하나라도 맞지 않으면 맞지 않는다
		assertThat(matches(filter(topicTag("politics"), regionTag("world")), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(topicTag(EXECUTIVE), formatTag("opinion")), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(topicTag("economy"), regionTag("domestic")), TAGGED, "straight")).isFalse();
	}

	@Test
	void tagsOfTheSameKindMustAllBeAttachedToo() {
		assertThat(matches(filter(topicTag(EXECUTIVE), topicTag(ASSEMBLY)), PAIRS, "straight")).isTrue();
		assertThat(matches(filter(regionTag("world"), regionTag("domestic")), PAIRS, "straight")).isTrue();
		// 대조: 국회는 저장 행만 있고 붙지 않았다 · 국제는 저장 행도 없다
		assertThat(matches(filter(topicTag(EXECUTIVE), topicTag(ASSEMBLY)), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(regionTag("world"), regionTag("domestic")), TAGGED, "straight")).isFalse();
		// 두 대분류 — 붙은 중분류가 둘 다 정치라 경제는 맞지 않는다
		assertThat(matches(filter(topicTag("politics"), topicTag("economy")), PAIRS, "straight")).isFalse();
	}

	@Test
	void aCategoryWithItsOwnSubtopicIsTheSameAsTheSubtopicAlone() {
		assertThat(matches(filter(topicTag("politics"), topicTag(EXECUTIVE)), TAGGED, "straight")).isTrue();
		assertThat(matches(filter(topicTag("politics"), topicTag(ASSEMBLY)), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(topicTag(ASSEMBLY)), TAGGED, "straight")).isFalse();
		assertThat(matches(filter(topicTag("economy"), topicTag(FINANCE)), LOW, "straight")).isTrue();
	}

	@Test
	void aTopicStatusMatchesThatStatusAndMustMatchWithTheTags() {
		EvaluationFilter low = new EvaluationFilter(List.of(), TopicStatus.LOW_CONFIDENCE);
		assertThat(matches(low, LOW, "straight")).isTrue();
		assertThat(matches(low, TAGGED, "straight")).isFalse();
		assertThat(matches(new EvaluationFilter(List.of(), TopicStatus.UNCLASSIFIED), NONE, "straight")).isTrue();

		List<TagRef> politicsAtHome = List.of(topicTag("politics"), regionTag("domestic"));
		assertThat(matches(new EvaluationFilter(politicsAtHome, TopicStatus.TAGGED), TAGGED, "straight")).isTrue();
		assertThat(matches(new EvaluationFilter(politicsAtHome, TopicStatus.LOW_CONFIDENCE), TAGGED, "straight"))
			.isFalse();
		assertThat(matches(new EvaluationFilter(List.of(regionTag("world")), TopicStatus.TAGGED), TAGGED,
				"straight")).isFalse();
		assertThat(matches(EvaluationFilter.NONE, NONE, "feature")).isTrue();
	}

	private static boolean matches(EvaluationFilter filter, Judged judged, String formatKey) {
		return ReviewFilter.matches(filter, TAG_SET, judged, formatKey);
	}

	private static EvaluationFilter filter(TagRef... tags) {
		return new EvaluationFilter(List.of(tags), null);
	}

	private static TagRef topicTag(String key) {
		return new TagRef(TagKind.TOPIC, key);
	}

	private static TagRef regionTag(String key) {
		return new TagRef(TagKind.REGION, key);
	}

	private static TagRef formatTag(String key) {
		return new TagRef(TagKind.FORMAT, key);
	}

	private static Judged judge(List<StoredTag> stored) {
		return TagJudgement.judge(stored, TAG_SET, thresholds("0.5", "0.1", "0.5", "0.1"));
	}

}
