package net.yozmnews.storyline.domain.storyline;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.storyline.domain.relation.RelationGraph;

/**
 * 닫은 사건 집합 — 정본 · docs/specs/storyline.md §4 · storyline-R33.
 * <p>
 * 스토리라인의 닫은 사건 집합은 자기와 포함을 따라 내려간 모든 하위(가족)의 직접 소속을 사건 ID 로 합친 것이다. 저장하지 않고 조회마다
 * 관계와 소속에서 만든다 — 공개 조회(스토리라인 값 · 타임라인 · 사건의 스토리라인 · 기간 집계)와 운영자 조회의 가족이 이 한 길을 쓴다.
 *
 * @param families 스토리라인 → 자기와 모든 하위
 * @param members 스토리라인 → 직접 소속 사건
 */
public record ClosedMembers(Map<Long, Set<Long>> families, Map<Long, List<Long>> members) {

	public ClosedMembers {
		Objects.requireNonNull(families, "families");
		Objects.requireNonNull(members, "members");
	}

	/** 스토리라인마다 자기와 모든 하위 — 주어진 순서대로 */
	public static Map<Long, Set<Long>> families(Collection<Long> storylineIds, RelationGraph graph) {
		Map<Long, Set<Long>> families = new LinkedHashMap<>();
		for (long id : storylineIds) {
			Set<Long> family = new TreeSet<>(graph.descendants(id));
			family.add(id);
			families.put(id, family);
		}
		return families;
	}

	/** 가족 모두 — 소속을 한 번에 읽을 스토리라인 */
	public static Set<Long> everyone(Map<Long, Set<Long>> families) {
		Set<Long> all = new TreeSet<>();
		families.values().forEach(all::addAll);
		return all;
	}

	/** 직접 소속 사건 */
	public Set<Long> direct(long storylineId) {
		return new TreeSet<>(this.members.getOrDefault(storylineId, List.of()));
	}

	/** 닫은 사건 집합 — 자기와 모든 하위의 직접 소속을 사건 ID 로 합친 것 */
	public Set<Long> closed(long storylineId) {
		Set<Long> closed = new TreeSet<>();
		for (long member : this.families.getOrDefault(storylineId, Set.of(storylineId))) {
			closed.addAll(this.members.getOrDefault(member, List.of()));
		}
		return closed;
	}

	/** 하위가 있는가 */
	public boolean hasChildren(long storylineId) {
		return this.families.getOrDefault(storylineId, Set.of()).size() > 1;
	}

}
