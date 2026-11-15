package net.yozmnews.user.domain.preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetView;

/**
 * 손으로 적은 작은 태그 체계 — 선호 규칙의 단위 시험이 쓴다. 이름은 키 앞에 "이름 " 을 붙인 것이다.
 */
final class TagSets {

	private TagSets() {
	}

	/**
	 * @param topics 대분류 키 → 중분류 짧은 키 목록(전체 키는 "대분류.짧은 키")
	 * @param regions 지역 키
	 * @param formats 형식 키 → 기본 제외인가
	 */
	static TagSetView of(long id, Map<String, List<String>> topics, List<String> regions,
			Map<String, Boolean> formats) {
		List<TopicCategory> categories = new ArrayList<>();
		topics.forEach((category, subtopics) -> categories.add(new TopicCategory(category, "이름 " + category,
				category + " 설명", subtopics.stream().map(sub -> asked(category + "." + sub)).toList())));
		List<FormatOption> options = new ArrayList<>();
		formats.forEach((key, excluded) -> options.add(new FormatOption(key, "이름 " + key, key + " 설명", excluded)));
		return new TagSetView(new TagSetId(id), (int) id, categories, regions.stream().map(TagSets::asked).toList(),
				new FormatChoice("형식은 무엇인가", options),
				new ImportanceScale("얼마나 중요한가", List.of(new ImportanceLevel(1, "하나"), new ImportanceLevel(2, "둘"),
						new ImportanceLevel(3, "셋"), new ImportanceLevel(4, "넷"), new ImportanceLevel(5, "다섯"))));
	}

	private static AskedTag asked(String key) {
		return new AskedTag(key, "이름 " + key, key + " 설명", key + " 인가");
	}

}
