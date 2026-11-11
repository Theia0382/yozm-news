package net.yozmnews.tag.domain.tagset;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetId;

/**
 * 태그 키 계보 해석의 정본 — docs/specs/tag.md §4 · tag-R26.
 * <p>
 * 버전 n − 1 → n 마다, 키에 버전 n 의 계보 행(이전 키 = 그 키)이 있으면 그 새 키들로 바꾸고, 없고 버전 n 에 그 키가 있으면 그대로,
 * 둘 다 아니면 버린다(계보 없이 지운 키). 이를 첫 버전 다음부터 마지막 버전까지 차례로 한다.
 */
public final class KeyLineage {

	private KeyLineage() {
	}

	/**
	 * 해석에 쓰는 버전 하나.
	 *
	 * @param lineage 바로 앞 버전에서 이 버전으로 이어지는 계보 — 첫 버전의 계보는 쓰지 않는다
	 */
	public record Version(TagSetId id, TagSetContent content, List<LineageDef> lineage) {

		public Version {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(content, "content");
			lineage = List.copyOf(lineage);
		}

	}

	/**
	 * 첫 버전의 모든 키를 마지막 버전 키의 집합으로 옮긴 표를 만든다.
	 * @param versions 번호 순 — 첫 원소가 키가 속한 버전, 마지막 원소가 기준으로 삼을 버전
	 * @throws IllegalArgumentException 버전이 하나도 없을 때
	 */
	public static TagKeyMapping toLatest(List<Version> versions) {
		if (versions.isEmpty()) {
			throw new IllegalArgumentException("해석할 버전이 없다");
		}
		Version first = versions.get(0);
		Map<TagKind, Map<String, Set<String>>> keys = new EnumMap<>(TagKind.class);
		for (TagKind kind : TagKind.values()) {
			Map<String, Set<String>> current = new LinkedHashMap<>();
			for (String key : first.content().keys(kind)) {
				current.put(key, new LinkedHashSet<>(List.of(key)));
			}
			for (Version version : versions.subList(1, versions.size())) {
				Map<String, List<String>> next = next(version, kind);
				Set<String> present = new HashSet<>(version.content().keys(kind));
				for (Map.Entry<String, Set<String>> entry : current.entrySet()) {
					Set<String> moved = new LinkedHashSet<>();
					for (String key : entry.getValue()) {
						List<String> targets = next.get(key);
						if (targets != null) {
							moved.addAll(targets);
						}
						else if (present.contains(key)) {
							moved.add(key);
						}
					}
					entry.setValue(moved);
				}
			}
			keys.put(kind, current);
		}
		return new TagKeyMapping(first.id(), versions.get(versions.size() - 1).id(), keys);
	}

	/** 버전의 그 태그 종류 계보 — 이전 키 → 새 키들 */
	private static Map<String, List<String>> next(Version version, TagKind kind) {
		Map<String, List<String>> next = new HashMap<>();
		for (LineageDef lineage : version.lineage()) {
			if (lineage.kind() != kind) {
				continue;
			}
			List<String> to = lineage.to().stream().map(TagKey::value).toList();
			for (TagKey from : lineage.from()) {
				next.put(from.value(), to);
			}
		}
		return next;
	}

}
