package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.application.TagPreferences.TagPreferenceView;

/**
 * 태그 선호 조회 — 활성 태그 체계와 세션 사용자의 선호. docs/specs/user.md §3 · user-R45 · user-R46
 *
 * @param tagSet 활성 체계 — 저장할 때 그 ID 를 싣는다
 * @param preferences 선호
 */
record InterestTagsResponse(TagSetResponse tagSet, PreferencesResponse preferences) {

	static InterestTagsResponse of(TagPreferenceView view) {
		return new InterestTagsResponse(TagSetResponse.of(view.tagSet()), PreferencesResponse.of(view.preferences()));
	}

	/**
	 * 체계 — 파일에 적힌 순서.
	 *
	 * @param id 버전 ID
	 * @param number 버전 번호
	 * @param topics 주제 대분류와 그 중분류
	 * @param regions 지역
	 * @param formats 형식과 기본 제외
	 */
	record TagSetResponse(long id, int number, List<CategoryResponse> topics, List<TagResponse> regions,
			List<FormatResponse> formats) {

		static TagSetResponse of(TagSetView view) {
			return new TagSetResponse(view.id().value(), view.number(),
					view.topics().stream().map(CategoryResponse::of).toList(),
					view.regions().stream().map(TagResponse::of).toList(),
					view.format()
						.options()
						.stream()
						.map(option -> new FormatResponse(option.key(), option.name(), option.excludedByDefault()))
						.toList());
		}

	}

	/**
	 * 주제 대분류.
	 *
	 * @param key 대분류 키
	 * @param name 이름
	 * @param subtopics 중분류 — 키는 전체 키
	 */
	record CategoryResponse(String key, String name, List<TagResponse> subtopics) {

		static CategoryResponse of(TopicCategory category) {
			return new CategoryResponse(category.key(), category.name(),
					category.subtopics().stream().map(TagResponse::of).toList());
		}

	}

	/**
	 * 태그 하나.
	 *
	 * @param key 키
	 * @param name 이름
	 */
	record TagResponse(String key, String name) {

		static TagResponse of(AskedTag tag) {
			return new TagResponse(tag.key(), tag.name());
		}

	}

	/**
	 * 형식 하나.
	 *
	 * @param key 키
	 * @param name 이름
	 * @param excludedByDefault 기본 제외 형식인가
	 */
	record FormatResponse(String key, String name, boolean excludedByDefault) {
	}

}
