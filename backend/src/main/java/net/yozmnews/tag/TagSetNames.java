package net.yozmnews.tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.tag.domain.tagset.TagKey;

/**
 * 태그 체계 버전 하나의 키와 이름 — 주제(대분류 · 중분류)와 지역. docs/specs/tag.md §3.
 * <p>
 * 목록은 체계 파일에 적힌 순서다. 이름만 필요한 쪽(다이제스트의 프롬프트 등)이 평가 질문 묶음({@link TagSetView})을 읽지 않고 쓴다.
 *
 * @param id 버전
 * @param categories 주제 대분류 — 키는 대분류 키
 * @param subtopics 주제 중분류 — 키는 중분류 전체 키, 대분류 순서 · 그 안의 순서
 * @param regions 지역
 */
public record TagSetNames(TagSetId id, List<TagName> categories, List<TagName> subtopics, List<TagName> regions) {

	public TagSetNames {
		Objects.requireNonNull(id, "id");
		categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
		subtopics = List.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
	}

	/** 주제 키(대분류 키 또는 중분류 전체 키)의 이름 — 이 버전에 없는 키면 빈 값 */
	public Optional<String> topicName(String key) {
		Objects.requireNonNull(key, "key");
		return find(this.categories, key).or(() -> find(this.subtopics, key));
	}

	/** 중분류 전체 키의 대분류 키 — 이 버전의 중분류 키가 아니면(대분류 키 포함) 빈 값 */
	public Optional<String> categoryOf(String subtopicKey) {
		Objects.requireNonNull(subtopicKey, "subtopicKey");
		if (find(this.subtopics, subtopicKey).isEmpty()) {
			return Optional.empty();
		}
		return new TagKey(subtopicKey).category().map(TagKey::value);
	}

	/** 지역 키의 이름 — 이 버전에 없는 키면 빈 값 */
	public Optional<String> regionName(String key) {
		Objects.requireNonNull(key, "key");
		return find(this.regions, key);
	}

	/** 주제 키 → 체계 순서의 위치(대분류 먼저, 그다음 중분류) — 이름을 체계 순서로 늘어놓을 때 쓴다 */
	public Map<String, Integer> topicOrder() {
		Map<String, Integer> order = new LinkedHashMap<>();
		this.categories.forEach(tag -> order.putIfAbsent(tag.key(), order.size()));
		this.subtopics.forEach(tag -> order.putIfAbsent(tag.key(), order.size()));
		return order;
	}

	/** 지역 키 → 체계 순서의 위치 */
	public Map<String, Integer> regionOrder() {
		Map<String, Integer> order = new LinkedHashMap<>();
		this.regions.forEach(tag -> order.putIfAbsent(tag.key(), order.size()));
		return order;
	}

	private static Optional<String> find(List<TagName> tags, String key) {
		return tags.stream().filter(tag -> tag.key().equals(key)).map(TagName::name).findFirst();
	}

}
