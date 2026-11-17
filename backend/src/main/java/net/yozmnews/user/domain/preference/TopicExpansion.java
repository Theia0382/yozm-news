package net.yozmnews.user.domain.preference;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.tag.TagSetView;

/**
 * 대분류 구독을 그 버전의 하위 중분류로 펼치는 정본 — 대분류를 구독하면 하위 중분류 전체를 구독한 것이다 — docs/specs/user.md §5 · user-R45
 * <p>
 * 정규화({@link PreferenceSet#normalized}) · 따라가기({@link PreferenceFollowup}) · 다이제스트가 읽는 소비자 조회가 함께 쓴다.
 */
public final class TopicExpansion {

	private TopicExpansion() {
	}

	/** 그 버전의 대분류 아래 중분류 전체 키 — 체계 파일 순서. 그 버전에 없는 대분류면 빈 목록 */
	public static List<String> subtopicsOf(TagSetView version, String category) {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(category, "category");
		return version.topics()
			.stream()
			.filter(topic -> topic.key().equals(category))
			.flatMap(topic -> topic.subtopics().stream().map(AskedTag::key))
			.toList();
	}

	/**
	 * 구독 행마다 그 행이 덮는 중분류 — 대분류면 그 버전의 하위 중분류 전체, 중분류면 그 하나. 키 순.
	 * @param topics 그 버전의 키로 적힌 구독 — 대분류 키 · 중분류 전체 키
	 * @throws IllegalArgumentException 그 버전에 없는 키가 있을 때
	 */
	public static Map<String, Set<String>> expand(Collection<String> topics, TagSetView version) {
		Objects.requireNonNull(topics, "topics");
		Objects.requireNonNull(version, "version");
		Map<String, Set<String>> expanded = new TreeMap<>();
		for (String key : topics) {
			boolean category = version.topics().stream().anyMatch(topic -> topic.key().equals(key));
			if (category) {
				expanded.put(key, Set.copyOf(subtopicsOf(version, key)));
				continue;
			}
			boolean subtopic = version.topics()
				.stream()
				.anyMatch(topic -> topic.subtopics().stream().anyMatch(tag -> tag.key().equals(key)));
			if (!subtopic) {
				throw new IllegalArgumentException("버전 " + version.number() + " 에 없는 주제 키다: " + key);
			}
			expanded.put(key, Set.of(key));
		}
		return expanded;
	}

}
