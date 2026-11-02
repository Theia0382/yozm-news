package net.yozmnews.evaluation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 한 버전의 키를 활성 버전 키의 집합으로 옮긴 표 — docs/specs/evaluation.md §3 · evaluation-R26.
 *
 * @param from 키가 속한 버전
 * @param to 표가 기준으로 삼은 활성 버전
 * @param keys 태그 종류 → {@code from} 버전의 키 → 활성 버전의 키 집합. 계보 없이 지운 키는 빈 집합, 그대로인 키는 자기 하나
 */
public record TagKeyMapping(TagSetId from, TagSetId to, Map<TagKind, Map<String, Set<String>>> keys) {

	public TagKeyMapping {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		EnumMap<TagKind, Map<String, Set<String>>> copy = new EnumMap<>(TagKind.class);
		for (TagKind kind : TagKind.values()) {
			Map<String, Set<String>> byKey = keys.getOrDefault(kind, Map.of());
			Map<String, Set<String>> copied = new HashMap<>();
			byKey.forEach((key, targets) -> copied.put(key, Set.copyOf(targets)));
			copy.put(kind, Map.copyOf(copied));
		}
		keys = Map.copyOf(copy);
	}

	/**
	 * 키 하나를 푼다.
	 * @return 활성 버전의 키 집합 — 계보 없이 지운 키면 빈 집합
	 * @throws IllegalArgumentException {@code from} 버전의 그 태그 종류에 없는 키일 때
	 */
	public Set<String> resolve(TagKind kind, String key) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
		Set<String> targets = this.keys.get(kind).get(key);
		if (targets == null) {
			throw new IllegalArgumentException("버전 " + this.from.value() + " 의 " + kind + " 에 없는 키다: " + key);
		}
		return targets;
	}

}
