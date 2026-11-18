package net.yozmnews.tag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 태그 체계 버전의 이름 — 주제 키(대분류 · 중분류)와 지역 키의 이름 찾기, 없는 키는 빈 값, 체계 순서(대분류 먼저, 그다음 중분류)
 */
class TagSetNamesTests {

	private static final TagSetNames NAMES = new TagSetNames(new TagSetId(3),
			List.of(new TagName("politics", "정치"), new TagName("world", "국제")),
			List.of(new TagName("politics.executive", "행정"), new TagName("world.politics", "국제정치")),
			List.of(new TagName("us", "미국"), new TagName("politics", "지역 이름이 같은 키")));

	@Test
	void topicKeysAreCategoriesOrSubtopicsAndRegionsAreLookedUpApart() {
		assertThat(NAMES.topicName("world")).hasValue("국제");
		assertThat(NAMES.topicName("world.politics")).hasValue("국제정치");
		assertThat(NAMES.regionName("us")).hasValue("미국");
		// 같은 문자열 키라도 종류마다 따로 찾는다
		assertThat(NAMES.topicName("politics")).hasValue("정치");
		assertThat(NAMES.regionName("politics")).hasValue("지역 이름이 같은 키");
		assertThat(NAMES.topicName("us")).isEmpty();
		assertThat(NAMES.regionName("world")).isEmpty();
	}

	@Test
	void aSubtopicKeyOfThisVersionKnowsItsCategory() {
		assertThat(NAMES.categoryOf("politics.executive")).hasValue("politics");
		assertThat(NAMES.categoryOf("world.politics")).hasValue("world");
		assertThat(NAMES.categoryOf("politics")).as("대분류 키").isEmpty();
		assertThat(NAMES.categoryOf("politics.legislature")).as("이 버전에 없는 중분류 키").isEmpty();
		assertThat(NAMES.categoryOf("us")).as("지역 키").isEmpty();
	}

	@Test
	void theOrderIsTheTagSetOrderWithCategoriesFirst() {
		assertThat(NAMES.topicOrder()).containsExactly(java.util.Map.entry("politics", 0),
				java.util.Map.entry("world", 1), java.util.Map.entry("politics.executive", 2),
				java.util.Map.entry("world.politics", 3));
		assertThat(NAMES.regionOrder()).containsExactly(java.util.Map.entry("us", 0),
				java.util.Map.entry("politics", 1));
	}

}
