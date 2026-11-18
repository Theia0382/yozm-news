package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.storyline.domain.relation.RelationMove.Moved;
import net.yozmnews.storyline.domain.relation.RelationMove.Plan;
import org.junit.jupiter.api.Test;

/**
 * 스토리라인이 합쳐질 때 관계를 옮기는 계획 — 합쳐질 스토리라인은 2, 남는 스토리라인은 1 이다.
 * <p>
 * 지키는 규칙: storyline-R32
 */
class RelationMoveTests {

	@Test
	void movesBothEndsOfTheMergedStorylinesRelationsToTheSurvivor() {
		Relation parent = new Relation(9, 2, RelationKind.CONTAINS);
		Relation child = new Relation(2, 5, RelationKind.CONTAINS);
		Relation follows = new Relation(2, 6, RelationKind.FOLLOWS);
		Relation untouched = new Relation(7, 8, RelationKind.FOLLOWS);

		assertThat(RelationMove.plan(List.of(parent, child, follows, untouched), 2, 1)).isEqualTo(new Plan(List.of(),
				List.of(new Moved(child, new Relation(1, 5, RelationKind.CONTAINS)),
						new Moved(parent, new Relation(9, 1, RelationKind.CONTAINS)),
						new Moved(follows, new Relation(1, 6, RelationKind.FOLLOWS)))));
	}

	@Test
	void dropsTheRelationBetweenTheTwoAndTheOnesThePairAlreadyHas() {
		Relation between = new Relation(1, 2, RelationKind.CONTAINS);
		Relation survivors = new Relation(1, 5, RelationKind.FOLLOWS);
		// 남는 쪽이 5 와 이미 관계를 가졌다 — 종류 · 방향이 달라도 남는 쪽의 것이 이긴다
		Relation mergeds = new Relation(5, 2, RelationKind.CONTAINS);

		assertThat(RelationMove.plan(List.of(between, survivors, mergeds), 2, 1))
			.isEqualTo(new Plan(List.of(between, mergeds), List.of()));
	}

	@Test
	void dropsAMoveThatWouldGiveASecondParentOrCloseACycle() {
		// 남는 쪽 1 에 이미 상위 8 이 있다 — 합쳐질 쪽의 상위 9 는 옮기지 않는다
		Relation survivorsParent = new Relation(8, 1, RelationKind.CONTAINS);
		Relation mergedsParent = new Relation(9, 2, RelationKind.CONTAINS);
		assertThat(RelationMove.plan(List.of(survivorsParent, mergedsParent), 2, 1))
			.isEqualTo(new Plan(List.of(mergedsParent), List.of()));

		// 1 ⊃ 5 인데 합쳐질 쪽이 5 ⊃ … ⊃ 2 의 하위였다(5 ⊃ 2) — 옮기면 5 ⊃ 1 ⊃ 5
		Relation down = new Relation(1, 5, RelationKind.CONTAINS);
		Relation up = new Relation(5, 2, RelationKind.CONTAINS);
		// (1, 5) 쌍에 이미 관계가 있어 지워진다 — 순환까지 가지 않는다
		assertThat(RelationMove.plan(List.of(down, up), 2, 1)).isEqualTo(new Plan(List.of(up), List.of()));

		// 세 걸음 — 1 → 5 → 6 (이어짐)인데 합쳐질 쪽이 6 → 2 였다: 옮기면 6 → 1 → 5 → 6
		List<Relation> chain = List.of(new Relation(1, 5, RelationKind.FOLLOWS), new Relation(5, 6, RelationKind.FOLLOWS),
				new Relation(6, 2, RelationKind.FOLLOWS));
		assertThat(RelationMove.plan(chain, 2, 1)).isEqualTo(new Plan(List.of(chain.get(2)), List.of()));
	}

	@Test
	void movesInTheOrderOfKindThenSourceThenTarget() {
		// 2 ⊃ 5 · 6 ⊃ 2 · 7 → 2 를 옮기면 1 ⊃ 5 · 6 ⊃ 1 · 7 → 1 이다
		Relation contains = new Relation(2, 5, RelationKind.CONTAINS);
		Relation parent = new Relation(6, 2, RelationKind.CONTAINS);
		Relation other = new Relation(7, 2, RelationKind.FOLLOWS);

		Plan plan = RelationMove.plan(List.of(other, parent, contains), 2, 1);

		// 순서는 종류(포함 먼저) · 앞 · 뒤다 — 넘긴 순서와 무관하다
		assertThat(plan.moved()).extracting(Moved::to)
			.containsExactly(new Relation(1, 5, RelationKind.CONTAINS), new Relation(6, 1, RelationKind.CONTAINS),
					new Relation(7, 1, RelationKind.FOLLOWS));
		assertThat(plan.removed()).isEmpty();
	}

	@Test
	void leavesRelationsThatDoNotTouchTheMergedStorylineAlone() {
		assertThat(RelationMove.plan(List.of(new Relation(7, 8, RelationKind.CONTAINS)), 2, 1))
			.isEqualTo(new Plan(List.of(), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> RelationMove.plan(List.of(), 3, 3))
			.withMessage("합쳐질 스토리라인과 남는 스토리라인이 같다: 3");
	}

}
