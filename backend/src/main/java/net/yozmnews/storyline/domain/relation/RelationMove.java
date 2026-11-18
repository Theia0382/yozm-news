package net.yozmnews.storyline.domain.relation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 스토리라인이 합쳐질 때 관계를 옮기는 계획 — 정본 · docs/specs/storyline.md §4 · storyline-R32.
 * <p>
 * 합쳐질 스토리라인의 관계를 남는 스토리라인으로 옮긴다. 옮기면 자기 자신이 되는 관계, 옮길 쌍에 남는 쪽이 이미 관계를 가진 것, 옮기면
 * 순환이 되거나 하위에 둘째 상위가 생기는 것은 옮기지 않고 지운다 — 남는 쪽의 관계와 구조 제약이 이긴다. 옮길 관계는 종류 · 앞 · 뒤 순으로
 * 하나씩 본다 — 앞서 옮긴 관계가 뒤의 것을 막을 수 있다.
 */
public final class RelationMove {

	private static final Comparator<Relation> ORDER = Comparator.comparing(Relation::kind)
		.thenComparingLong(Relation::sourceId)
		.thenComparingLong(Relation::targetId);

	private RelationMove() {
	}

	/**
	 * @param relations 지금 관계
	 * @param merged 합쳐질 스토리라인
	 * @param survivor 남는 스토리라인
	 */
	public static Plan plan(Collection<Relation> relations, long merged, long survivor) {
		if (merged == survivor) {
			throw new IllegalArgumentException("합쳐질 스토리라인과 남는 스토리라인이 같다: " + merged);
		}
		List<Relation> kept = new ArrayList<>(relations.stream().filter(relation -> !relation.touches(merged)).toList());
		List<Relation> removed = new ArrayList<>();
		List<Moved> moved = new ArrayList<>();
		for (Relation relation : relations.stream().filter(one -> one.touches(merged)).sorted(ORDER).toList()) {
			long source = (relation.sourceId() == merged) ? survivor : relation.sourceId();
			long target = (relation.targetId() == merged) ? survivor : relation.targetId();
			if (source == target) {
				removed.add(relation);
				continue;
			}
			Relation to = new Relation(source, target, relation.kind());
			RelationGraph graph = new RelationGraph(kept);
			boolean secondParent = to.kind() == RelationKind.CONTAINS && graph.parentOf(target).isPresent();
			if (graph.between(source, target).isPresent() || secondParent || graph.closesCycle(to)) {
				removed.add(relation);
				continue;
			}
			kept.add(to);
			moved.add(new Moved(relation, to));
		}
		return new Plan(removed, moved);
	}

	/**
	 * 합친 뒤 나란한 흐름 쌍이 맺은 고리의 맺은 쌍을 고친다 — 관계를 옮긴 뒤의 고리를 넘긴다.
	 * <p>
	 * 맺은 쌍에 합쳐진 스토리라인이 들었으면 남는 스토리라인으로 고쳐 적는다. 고치면 쌍의 두 쪽이 같아지거나(맺은 쌍이 서로 합쳐졌다),
	 * 맺은 쌍이 그 고리의 두 끝과 같아지면(상위가 흐름 하나에 합쳐져 고리가 두 흐름 사이로 옮겨졌다) 표지를 지운다 — 그 고리는 두 끝이
	 * 스스로 판정받는 보통 관계다.
	 * @param grouped 지금 나란한 흐름 쌍이 맺은 고리
	 * @return 고칠 고리만 — 그대로인 것은 없다
	 */
	public static List<Remark> remark(Collection<GroupedRelation> grouped, long merged, long survivor) {
		List<Remark> remarks = new ArrayList<>();
		for (GroupedRelation one : grouped) {
			long low = (one.by().low() == merged) ? survivor : one.by().low();
			long high = (one.by().high() == merged) ? survivor : one.by().high();
			GroupedBy by = (low == high) ? null : GroupedBy.of(low, high);
			if (by != null && by.is(one.relation().sourceId(), one.relation().targetId())) {
				by = null;
			}
			if (!one.by().equals(by)) {
				remarks.add(new Remark(one.relation(), by));
			}
		}
		return remarks;
	}

	/**
	 * 고리 하나의 맺은 쌍을 고친다.
	 *
	 * @param by 고친 맺은 쌍 — null 이면 표지를 지운다
	 */
	public record Remark(Relation relation, @Nullable GroupedBy by) {

		public Remark {
			Objects.requireNonNull(relation, "relation");
		}

	}

	/**
	 * 옮기는 계획.
	 *
	 * @param removed 지울 관계
	 * @param moved 끝을 바꿀 관계
	 */
	public record Plan(List<Relation> removed, List<Moved> moved) {

		public Plan {
			removed = List.copyOf(removed);
			moved = List.copyOf(moved);
		}

	}

	/**
	 * 끝을 바꾸는 관계 하나 — 확률 · 판정 원본 · 기준은 그대로 간다.
	 *
	 * @param from 지금 관계
	 * @param to 옮긴 뒤의 관계
	 */
	public record Moved(Relation from, Relation to) {

		public Moved {
			Objects.requireNonNull(from, "from");
			Objects.requireNonNull(to, "to");
		}

	}

}
