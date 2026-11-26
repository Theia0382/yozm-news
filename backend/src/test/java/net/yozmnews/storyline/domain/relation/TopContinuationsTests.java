package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 스토리라인마다 그 꼭대기에서 이어짐을 앞 → 뒤로 따라 닿는 꼭대기.
 * <p>
 * 지키는 규칙: 이어짐의 꼭대기 단위 닫힘(docs/specs/storyline.md §5) · storyline-R37(꼭대기) · storyline-R38(이어짐으로 닿는 꼭대기) ·
 * storyline-R25(순환 금지는 같은 종류의 원래 스토리라인 사이에서만)
 */
class TopContinuationsTests {

	@Test
	void continuationsFollowTheLinksForwardToTheEndAndNeverBackward() {
		// 1 → 2 → 3
		RelationGraph graph = new RelationGraph(
				List.of(new Relation(1, 2, RelationKind.FOLLOWS), new Relation(2, 3, RelationKind.FOLLOWS)));

		Map<Long, Set<Long>> reached = TopContinuations.of(List.of(1L, 2L, 3L), graph);

		// 앞에서는 끝까지 닿고, 뒤에서는 앞으로 돌아가지 않는다
		assertThat(reached).containsExactlyInAnyOrderEntriesOf(Map.of(1L, Set.of(2L, 3L), 2L, Set.of(3L), 3L, Set.of()));
	}

	@Test
	void linksBetweenChildrenAreLiftedToTheTopsOfTheirTrees() {
		// 1 ⊃ 11, 2 ⊃ 21, 11 → 21 — 꼭대기로 올리면 1 → 2
		RelationGraph graph = new RelationGraph(List.of(new Relation(1, 11, RelationKind.CONTAINS),
				new Relation(2, 21, RelationKind.CONTAINS), new Relation(11, 21, RelationKind.FOLLOWS)));

		Map<Long, Set<Long>> reached = TopContinuations.of(List.of(1L, 11L, 21L), graph);

		// 하위(11)로 물어도 그 꼭대기(1)에서 닿는 꼭대기(2)를 낸다 — 하위(21)가 아니라 꼭대기다
		assertThat(reached).containsExactlyInAnyOrderEntriesOf(Map.of(1L, Set.of(2L), 11L, Set.of(2L), 21L, Set.of()));
	}

	@Test
	void aLinkInsideOneTreeIsDroppedAndContainmentAloneReachesNothing() {
		// 1 ⊃ 11 · 1 ⊃ 12, 11 → 12 — 두 끝의 꼭대기가 같다
		RelationGraph graph = new RelationGraph(List.of(new Relation(1, 11, RelationKind.CONTAINS),
				new Relation(1, 12, RelationKind.CONTAINS), new Relation(11, 12, RelationKind.FOLLOWS)));

		Map<Long, Set<Long>> reached = TopContinuations.of(List.of(1L, 11L, 12L, 30L), graph);

		// 대조 — 포함만 있는 나무와 관계가 없는 스토리라인(30)은 닿는 것이 없다
		assertThat(reached)
			.containsExactlyInAnyOrderEntriesOf(Map.of(1L, Set.of(), 11L, Set.of(), 12L, Set.of(), 30L, Set.of()));
	}

	// 순환에서 멈추지 않으면 끝나지 않는다 — 따로 둔 스레드의 제한 시간으로 실패하게 한다
	@Test
	@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	void aCycleMadeByLiftingEndsWithoutReturningToTheStart() {
		// 1 ⊃ 11, 2 ⊃ 21, 11 → 2 · 21 → 1 — 원래 스토리라인으로는 순환이 아니지만 꼭대기로 올리면 1 → 2 → 1
		RelationGraph graph = new RelationGraph(
				List.of(new Relation(1, 11, RelationKind.CONTAINS), new Relation(2, 21, RelationKind.CONTAINS),
						new Relation(11, 2, RelationKind.FOLLOWS), new Relation(21, 1, RelationKind.FOLLOWS)));

		Map<Long, Set<Long>> reached = TopContinuations.of(List.of(1L, 2L), graph);

		assertThat(reached).containsExactlyInAnyOrderEntriesOf(Map.of(1L, Set.of(2L), 2L, Set.of(1L)));
	}

	@Test
	void branchesAndJoinsAreAllReached() {
		// 1 → 2 · 1 → 3 · 2 → 4 · 3 → 4, 5 → 4
		RelationGraph graph = new RelationGraph(
				List.of(new Relation(1, 2, RelationKind.FOLLOWS), new Relation(1, 3, RelationKind.FOLLOWS),
						new Relation(2, 4, RelationKind.FOLLOWS), new Relation(3, 4, RelationKind.FOLLOWS),
						new Relation(5, 4, RelationKind.FOLLOWS)));

		Map<Long, Set<Long>> reached = TopContinuations.of(List.of(1L, 5L, 4L), graph);

		assertThat(reached)
			.containsExactlyInAnyOrderEntriesOf(Map.of(1L, Set.of(2L, 3L, 4L), 5L, Set.of(4L), 4L, Set.of()));
	}

}
