package net.yozmnews.reader.adapter.in.web;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.TopicSuggestionList;
import net.yozmnews.digest.TopicSuggestions;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetNames;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 구독 제안 — 세션 사용자에게 지금 제안할 주제 중분류의 이름 · 대분류 이름. docs/specs/reader.md §3 · reader-R12
 * <p>
 * {@code digest} 의 {@link TopicSuggestions} 와 {@code tag} 의 이름 조회만 부르고 응답 모양을 정한다 — 제안의 판단은 {@code digest} 의
 * 것이다. 수락은 {@code user} 의 태그 선호 저장, 거절은 {@code user} 의 거절 처리기가 받는다. 세션이 필요하고 키 관문 뒤다(키 없이 여는
 * 표시 · 요청 방어 표시가 없다).
 */
@WebRole
@RestController
class TopicSuggestionController {

	private final TopicSuggestions suggestions;

	private final TagSetDirectory tagSets;

	TopicSuggestionController(TopicSuggestions suggestions, TagSetDirectory tagSets) {
		this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
	}

	@GetMapping("/api/topic-suggestions")
	TopicSuggestionsResponse topicSuggestions(UserId user) {
		Optional<TopicSuggestionList> found = this.suggestions.suggestionsOf(user);
		if (found.isEmpty()) {
			return new TopicSuggestionsResponse(null, List.of());
		}
		TagSetNames names = this.tagSets.names(found.get().tagSet());
		List<TopicSuggestionResponse> topics = found.get()
			.subtopics()
			.stream()
			.map(key -> topic(key, names))
			.toList();
		return new TopicSuggestionsResponse(found.get().tagSet().value(), topics);
	}

	/** 이름은 제안이 낸 버전으로 읽는다 — 그 버전에 없는 키는 불변식이 깨진 것이다 */
	private static TopicSuggestionResponse topic(String key, TagSetNames names) {
		String category = names.categoryOf(key)
			.orElseThrow(() -> new IllegalStateException("구독 제안의 키가 그 버전의 중분류가 아니다: " + key));
		return new TopicSuggestionResponse(key, name(names, key), name(names, category));
	}

	private static String name(TagSetNames names, String key) {
		return names.topicName(key)
			.orElseThrow(() -> new IllegalStateException("구독 제안의 키가 그 버전에 없다: " + key));
	}

}
