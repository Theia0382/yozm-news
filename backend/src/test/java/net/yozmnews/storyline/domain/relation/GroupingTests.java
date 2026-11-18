package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.domain.relation.RelationRule.Decision;
import org.junit.jupiter.api.Test;

/**
 * 나란한 흐름으로 판정된 두 스토리라인을 묶는 판정 — 나란한 흐름은 한쪽을 다른 쪽 아래 넣지 않고 같은 상위의 하위로 둔다. 넘기는 그래프는 그
 * 쌍 사이의 관계를 뺀 것이다.
 * <p>
 * 지키는 규칙: storyline-R29 · storyline-R38
 */
class GroupingTests {

	private static final RelationGraph EMPTY = new RelationGraph(List.of());

	@Test
	void twoFlowsWithoutAParentOpenOneUnlessExactlyOneOfThemHasChildren() {
		assertThat(Grouping.decide(1, 2, EMPTY)).isEqualTo(new Grouping.Action.Open());
		// 한쪽만 하위를 가진 상위다 — 그 위에 상위를 열지 않고 다른 쪽을 그 하위들과 견준다
		RelationGraph withChildren = new RelationGraph(List.of(contains(1, 8), contains(1, 7)));
		assertThat(Grouping.decide(1, 2, withChildren)).isEqualTo(new Grouping.Action.Among(1, 2, List.of(7L, 8L)));
		assertThat(Grouping.decide(2, 1, withChildren)).isEqualTo(new Grouping.Action.Among(1, 2, List.of(7L, 8L)));
		// 둘 다 하위를 가졌으면 어느 쪽 아래에도 두지 않고 둘 위에 연다
		RelationGraph bothParents = new RelationGraph(List.of(contains(1, 7), contains(2, 6)));
		assertThat(Grouping.decide(1, 2, bothParents)).isEqualTo(new Grouping.Action.Open());
		// 이어짐은 상위가 아니다
		RelationGraph followed = new RelationGraph(List.of(new Relation(9, 1, RelationKind.FOLLOWS)));
		assertThat(Grouping.decide(1, 2, followed)).isEqualTo(new Grouping.Action.Open());
	}

	@Test
	void theOtherFlowIsReferredToAParentWhoseOnlyChildIsTheOneInThePair() {
		RelationGraph parented = new RelationGraph(List.of(contains(9, 1)));

		assertThat(Grouping.decide(1, 2, parented)).isEqualTo(new Grouping.Action.Refer(9, 2));
		assertThat(Grouping.decide(2, 1, parented)).isEqualTo(new Grouping.Action.Refer(9, 2));
	}

	@Test
	void theOtherFlowIsComparedWithTheOtherChildrenOfTheParent() {
		// 9 ⊃ 1 · 4 · 3 — 쌍의 한쪽인 1 은 견줄 하위에서 빠진다
		RelationGraph parented = new RelationGraph(List.of(contains(9, 1), contains(9, 4), contains(9, 3)));

		assertThat(Grouping.decide(1, 2, parented)).isEqualTo(new Grouping.Action.Among(9, 2, List.of(3L, 4L)));
		assertThat(Grouping.decide(2, 1, parented)).isEqualTo(new Grouping.Action.Among(9, 2, List.of(3L, 4L)));
	}

	@Test
	void theOtherFlowJoinsOnlyWhenItIsParallelWithEveryComparedChild() {
		Grouping.Action.Among among = new Grouping.Action.Among(9, 2, List.of(3L, 4L));

		// 나란한 흐름 판정에서 나온 결말 셋은 모두 나란한 흐름으로 센다
		assertThat(Grouping.among(among, Map.of(3L, RelationOutcome.REFERRED, 4L, RelationOutcome.GROUPED)))
			.isEqualTo(new Grouping.Consensus.Join());
		assertThat(Grouping.among(among, Map.of(3L, RelationOutcome.UNGROUPED, 4L, RelationOutcome.REFERRED)))
			.isEqualTo(new Grouping.Consensus.Join());
		// 묻지 않은 하위가 있으면 그것만 묻는다 — 다른 상대의 결말은 보지 않는다
		assertThat(Grouping.among(among, Map.of(4L, RelationOutcome.REFERRED, 7L, RelationOutcome.NOT_RELATED)))
			.isEqualTo(new Grouping.Consensus.Ask(List.of(3L)));
		assertThat(Grouping.among(among, Map.of())).isEqualTo(new Grouping.Consensus.Ask(List.of(3L, 4L)));
		// 하나라도 나란한 흐름이 아니면 두지 않는다 — 묻지 않은 하위가 남았어도 더 묻지 않는다
		for (RelationOutcome other : List.of(RelationOutcome.LINKED, RelationOutcome.NOT_RELATED,
				RelationOutcome.LOW_PROBABILITY, RelationOutcome.NOT_ONE_MATTER, RelationOutcome.CYCLE,
				RelationOutcome.SECOND_PARENT, RelationOutcome.UNMERGED, RelationOutcome.NOTHING_TO_ASK,
				RelationOutcome.REJECTED)) {
			assertThat(Grouping.among(among, Map.of(3L, RelationOutcome.REFERRED, 4L, other)))
				.isEqualTo(new Grouping.Consensus.Stay());
			assertThat(Grouping.among(among, Map.of(4L, other))).isEqualTo(new Grouping.Consensus.Stay());
		}
		assertThatIllegalArgumentException().isThrownBy(() -> new Grouping.Action.Among(9, 2, List.of()));
	}

	@Test
	void flowsUnderTheSameParentAreAlreadyGroupedAndUnderDifferentParentsAreNot() {
		RelationGraph same = new RelationGraph(List.of(contains(9, 1), contains(9, 2)));
		RelationGraph different = new RelationGraph(List.of(contains(9, 1), contains(8, 2)));

		assertThat(Grouping.decide(1, 2, same)).isEqualTo(new Grouping.Action.Already());
		assertThat(Grouping.decide(1, 2, different)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.SECOND_PARENT));
	}

	@Test
	void aFlowCannotBeGroupedWithItsAncestor() {
		// 1 ⊃ 5 ⊃ 2 — 이 쌍 사이의 관계가 아니라 다른 고리들로 조상이다
		RelationGraph chain = new RelationGraph(List.of(contains(1, 5), contains(5, 2)));

		assertThat(Grouping.decide(1, 2, chain)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.CYCLE));
		assertThat(Grouping.decide(2, 1, chain)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.CYCLE));
	}

	@Test
	void theOutcomeOfEachAction() {
		assertThat(Grouping.outcomeOf(new Grouping.Action.Open(), true, null)).isEqualTo(RelationOutcome.GROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Open(), false, null)).isEqualTo(RelationOutcome.UNGROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Already(), false, null)).isEqualTo(RelationOutcome.GROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Refer(9, 2), false, null)).isEqualTo(RelationOutcome.REFERRED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Reject(Grouping.Reason.CYCLE), false, null))
			.isEqualTo(RelationOutcome.CYCLE);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Reject(Grouping.Reason.SECOND_PARENT), false, null))
			.isEqualTo(RelationOutcome.SECOND_PARENT);
		Grouping.Action.Among among = new Grouping.Action.Among(9, 2, List.of(3L));
		assertThat(Grouping.outcomeOf(among, false, new Grouping.Consensus.Join())).isEqualTo(RelationOutcome.GROUPED);
		assertThat(Grouping.outcomeOf(among, false, new Grouping.Consensus.Ask(List.of(3L))))
			.isEqualTo(RelationOutcome.REFERRED);
		assertThat(Grouping.outcomeOf(among, false, new Grouping.Consensus.Stay())).isEqualTo(RelationOutcome.UNGROUPED);
	}

	@Test
	void anOverturnedPairReleasesItsLinksAndOnlyRemovesTheOneToTheChildOfANewContainment() {
		List<Relation> grouped = List.of(contains(9, 1), contains(9, 2));

		// 해당 없음 · 이어짐 — 고리는 지우지 않고 표지만 푼다
		assertThat(Grouping.overturn(new Decision.None(Decision.Reason.NOT_RELATED), grouped))
			.isEqualTo(new Grouping.Overturn(List.of(), grouped));
		assertThat(Grouping.overturn(new Decision.Link(new Relation(1, 2, RelationKind.FOLLOWS), 0.8), grouped))
			.isEqualTo(new Grouping.Overturn(List.of(), grouped));
		// 2 가 1 의 부분 — 2 를 가리키는 고리는 지우고(2 는 1 아래에 든다) 1 을 가리키는 고리는 표지를 푼다
		assertThat(Grouping.overturn(new Decision.Link(contains(1, 2), 0.8), grouped))
			.isEqualTo(new Grouping.Overturn(List.of(contains(9, 2)), List.of(contains(9, 1))));
		assertThat(Grouping.overturn(new Decision.Link(contains(2, 1), 0.8), grouped))
			.isEqualTo(new Grouping.Overturn(List.of(contains(9, 1)), List.of(contains(9, 2))));
		// 한 고리만 남아 있어도 남은 것에만 한다
		assertThat(Grouping.overturn(new Decision.Link(contains(1, 2), 0.8), List.of(contains(9, 1))))
			.isEqualTo(new Grouping.Overturn(List.of(), List.of(contains(9, 1))));
		// 조각으로 뒤집혀도 고리는 지우지 않고 표지만 푼다 — 그 둘이 합쳐지든 둘로 남든 이 쌍의 고리가 아니다
		assertThat(Grouping.overturn(new Decision.Fragments(1, 2, 0.8), grouped))
			.isEqualTo(new Grouping.Overturn(List.of(), grouped));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Grouping.overturn(new Decision.Parallel(1, 2, 0.8), grouped));
	}

	private static Relation contains(long parent, long child) {
		return new Relation(parent, child, RelationKind.CONTAINS);
	}

}
