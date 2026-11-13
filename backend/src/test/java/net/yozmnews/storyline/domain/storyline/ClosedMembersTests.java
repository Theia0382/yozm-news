package net.yozmnews.storyline.domain.storyline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationKind;
import org.junit.jupiter.api.Test;

/**
 * 닫은 사건 집합 — 가족(자기와 모든 하위)의 직접 소속을 사건 ID 로 합친다. 이어짐은 가족이 아니다.
 * <p>
 * 지키는 규칙: storyline-R33
 */
class ClosedMembersTests {

	// 1 ⊃ 2 ⊃ 3, 1 → 4(이어짐)
	private static final RelationGraph GRAPH = new RelationGraph(List.of(new Relation(1, 2, RelationKind.CONTAINS),
			new Relation(2, 3, RelationKind.CONTAINS), new Relation(1, 4, RelationKind.FOLLOWS)));

	@Test
	void aFamilyIsTheStorylineAndEveryDescendantButNotWhatFollows() {
		Map<Long, Set<Long>> families = ClosedMembers.families(List.of(1L, 3L, 4L), GRAPH);

		assertThat(families).containsExactly(Map.entry(1L, Set.of(1L, 2L, 3L)), Map.entry(3L, Set.of(3L)),
				Map.entry(4L, Set.of(4L)));
		assertThat(ClosedMembers.everyone(families)).containsExactly(1L, 2L, 3L, 4L);
	}

	@Test
	void theClosedSetJoinsTheDirectMembersOfTheFamilyOnce() {
		ClosedMembers closed = new ClosedMembers(ClosedMembers.families(List.of(1L, 2L), GRAPH),
				Map.of(1L, List.of(10L, 11L), 2L, List.of(11L, 20L), 3L, List.of(30L), 4L, List.of(40L)));

		assertThat(closed.direct(1L)).containsExactly(10L, 11L);
		assertThat(closed.closed(1L)).containsExactly(10L, 11L, 20L, 30L);
		assertThat(closed.closed(2L)).containsExactly(11L, 20L, 30L);
		assertThat(closed.hasChildren(1L)).isTrue();
		// 가족을 정하지 않은 스토리라인은 자기 소속만, 하위 없음
		assertThat(closed.closed(4L)).containsExactly(40L);
		assertThat(closed.hasChildren(4L)).isFalse();
		assertThat(closed.direct(9L)).isEmpty();
	}

}
