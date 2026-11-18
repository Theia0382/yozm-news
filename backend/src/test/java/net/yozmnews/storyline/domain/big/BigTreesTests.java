package net.yozmnews.storyline.domain.big;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTally;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationKind;
import org.junit.jupiter.api.Test;

/**
 * 큰 스토리라인을 포함 나무마다 한 행으로 모으는 규칙 — 큰 것 가운데 그 나무에서 가장 위의 것이 행이고 나머지는 그 행 아래다.
 * <p>
 * 지키는 규칙: storyline-R19
 */
class BigTreesTests {

	@Test
	void aBigParentAndItsBigChildrenMakeOneRow() {
		// 1 ⊃ 2 · 1 ⊃ 3 — 셋 모두 크다
		RelationGraph graph = new RelationGraph(List.of(contains(1, 2), contains(1, 3)));

		List<BigStoryline> rows = BigTrees.gather(List.of(big(1, "20.00"), big(2, "16.00"), big(3, "18.00")), graph);

		assertThat(rows).extracting(BigTreesTests::id).containsExactly(1L);
		// 아래 행은 보도 강도 내림차순
		assertThat(rows.getFirst().branches()).extracting(BigTreesTests::id).containsExactly(3L, 2L);
		assertThat(rows.getFirst().intensity()).hasToString("20.00");
	}

	@Test
	void theHeadIsTheTopmostBigAncestorAndAnAncestorThatIsNotBigIsSkipped() {
		// 1 ⊃ 2 ⊃ 3 ⊃ 4 — 1 은 크지 않다. 2 · 3 · 4 가 크면 머리는 2 이고 3 · 4 가 모두 그 아래다(한 층)
		RelationGraph chain = new RelationGraph(List.of(contains(1, 2), contains(2, 3), contains(3, 4)));

		List<BigStoryline> rows = BigTrees.gather(List.of(big(4, "15.00"), big(3, "16.00"), big(2, "17.00")), chain);

		assertThat(rows).extracting(BigTreesTests::id).containsExactly(2L);
		assertThat(rows.getFirst().branches()).extracting(BigTreesTests::id).containsExactly(3L, 4L);
		assertThat(rows.getFirst().branches()).allSatisfy(branch -> assertThat(branch.branches()).isEmpty());
		// 가운데 3 이 크지 않아도 4 의 머리는 2 다
		assertThat(BigTrees.gather(List.of(big(4, "15.00"), big(2, "17.00")), chain).getFirst().branches())
			.extracting(BigTreesTests::id)
			.containsExactly(4L);
		// 큰 조상이 없는 큰 하위는 스스로 행이다 — 같은 상위 아래의 형제여도 상위가 크지 않으면 두 행
		RelationGraph siblings = new RelationGraph(List.of(contains(1, 2), contains(1, 3)));
		assertThat(BigTrees.gather(List.of(big(2, "16.00"), big(3, "18.00")), siblings)).extracting(BigTreesTests::id)
			.containsExactly(3L, 2L);
	}

	@Test
	void unrelatedStorylinesAndStorylinesLinkedOnlyByFollowingStayAsSeparateRows() {
		RelationGraph follows = new RelationGraph(List.of(new Relation(1, 2, RelationKind.FOLLOWS)));

		List<BigStoryline> rows = BigTrees.gather(List.of(big(1, "15.00"), big(2, "15.00"), big(9, "20.00")), follows);

		// 행의 순서는 보도 강도 내림차순, 같으면 ID 순
		assertThat(rows).extracting(BigTreesTests::id).containsExactly(9L, 1L, 2L);
		assertThat(rows).allSatisfy(row -> assertThat(row.branches()).isEmpty());
		assertThat(BigTrees.gather(List.of(), follows)).isEmpty();
	}

	@Test
	void aRowUnderARowCarriesNoRowsOfItsOwn() {
		BigStoryline leaf = big(3, "15.00");
		BigStoryline middle = new BigStoryline(big(2, "16.00").storyline(), leaf.tally(), new BigDecimal("16.00"),
				List.of(leaf));

		// 아래 행은 한 층이다 — 아래 행을 가진 것을 다시 아래 행으로 실을 수 없다
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BigStoryline(big(1, "20.00").storyline(), leaf.tally(), new BigDecimal("20.00"),
					List.of(middle)))
			.withMessage("아래 행은 다시 아래 행을 갖지 않는다: StorylineId[value=1]");
	}

	private static long id(BigStoryline big) {
		return big.storyline().id().value();
	}

	private static Relation contains(long parent, long child) {
		return new Relation(parent, child, RelationKind.CONTAINS);
	}

	private static BigStoryline big(long id, String intensity) {
		Instant at = Instant.parse("2026-11-05T00:00:00Z");
		return new BigStoryline(
				new StorylineView(new StorylineId(id), StorylineState.ACTIVE, 2, 4, at, at, new EventId(1), at, null),
				new EventTally(20, 4, new BigDecimal("3.00"), new BigDecimal("60.00")), new BigDecimal(intensity));
	}

}
