package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.domain.relation.RelationRule.Decision;
import org.junit.jupiter.api.Test;

/**
 * 같은 사안의 조각으로 판정된 두 스토리라인을 묶는 판정 — 조각은 한쪽을 다른 쪽 아래 넣지 않고 같은 상위의 하위로 둔다. 넘기는 그래프는 그
 * 쌍 사이의 관계를 뺀 것이다.
 * <p>
 * 지키는 규칙: storyline-R29
 */
class GroupingTests {

	private static final RelationGraph EMPTY = new RelationGraph(List.of());

	@Test
	void twoFragmentsWithoutAParentOpenOneEvenWhenOneOfThemHasChildren() {
		assertThat(Grouping.decide(1, 2, EMPTY)).isEqualTo(new Grouping.Action.Open());
		// 한쪽이 하위를 가졌어도 조각은 그 아래 들지 않는다 — 둘 위에 상위를 연다
		RelationGraph withChild = new RelationGraph(List.of(contains(1, 7)));
		assertThat(Grouping.decide(1, 2, withChild)).isEqualTo(new Grouping.Action.Open());
		// 이어짐은 상위가 아니다
		RelationGraph followed = new RelationGraph(List.of(new Relation(9, 1, RelationKind.FOLLOWS)));
		assertThat(Grouping.decide(1, 2, followed)).isEqualTo(new Grouping.Action.Open());
	}

	@Test
	void theOtherFragmentIsReferredToTheParentOfTheOneThatHasIt() {
		RelationGraph parented = new RelationGraph(List.of(contains(9, 1)));

		assertThat(Grouping.decide(1, 2, parented)).isEqualTo(new Grouping.Action.Refer(9, 2));
		assertThat(Grouping.decide(2, 1, parented)).isEqualTo(new Grouping.Action.Refer(9, 2));
	}

	@Test
	void fragmentsUnderTheSameParentAreAlreadyGroupedAndUnderDifferentParentsAreNot() {
		RelationGraph same = new RelationGraph(List.of(contains(9, 1), contains(9, 2)));
		RelationGraph different = new RelationGraph(List.of(contains(9, 1), contains(8, 2)));

		assertThat(Grouping.decide(1, 2, same)).isEqualTo(new Grouping.Action.Already());
		assertThat(Grouping.decide(1, 2, different)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.SECOND_PARENT));
	}

	@Test
	void aFragmentCannotBeGroupedWithItsAncestor() {
		// 1 ⊃ 5 ⊃ 2 — 이 쌍 사이의 관계가 아니라 다른 고리들로 조상이다
		RelationGraph chain = new RelationGraph(List.of(contains(1, 5), contains(5, 2)));

		assertThat(Grouping.decide(1, 2, chain)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.CYCLE));
		assertThat(Grouping.decide(2, 1, chain)).isEqualTo(new Grouping.Action.Reject(Grouping.Reason.CYCLE));
	}

	@Test
	void theOutcomeOfEachAction() {
		assertThat(Grouping.outcomeOf(new Grouping.Action.Open(), true)).isEqualTo(RelationOutcome.GROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Open(), false)).isEqualTo(RelationOutcome.UNGROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Already(), false)).isEqualTo(RelationOutcome.GROUPED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Refer(9, 2), false)).isEqualTo(RelationOutcome.REFERRED);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Reject(Grouping.Reason.CYCLE), false))
			.isEqualTo(RelationOutcome.CYCLE);
		assertThat(Grouping.outcomeOf(new Grouping.Action.Reject(Grouping.Reason.SECOND_PARENT), false))
			.isEqualTo(RelationOutcome.SECOND_PARENT);
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
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Grouping.overturn(new Decision.Fragments(1, 2, 0.8), grouped));
	}

	private static Relation contains(long parent, long child) {
		return new Relation(parent, child, RelationKind.CONTAINS);
	}

}
