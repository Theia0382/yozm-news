package net.yozmnews.user.domain.userstoryline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.user.domain.userstoryline.UserStorylineGroups.Group;
import org.junit.jupiter.api.Test;

/**
 * 씨앗을 묶음으로.
 * <p>
 * 지키는 규칙: 헤드(닿는 꼭대기 가운데 뒤 스토리라인이 없는 것)마다 묶음 하나이고, 갈라지기 전 꼭대기는 그 뒤 헤드의 묶음 여럿에 든다. 한 헤드로 모이는
 * 스토리라인들은 한 묶음이다(docs/specs/user.md §5). 이어짐은 앞 → 뒤 닫힘으로 준다 — 닿는 꼭대기의 이어짐도 키로 둔다.
 */
class UserStorylineGroupsTests {

	@Test
	void theHeadsOfABranchingStorylineAreItsEnds() {
		Map<Long, Set<Long>> continuations = Map.of(5L, Set.of(82L, 307L, 49L, 423L), 49L, Set.of(423L), 82L, Set.of(),
				307L, Set.of(), 423L, Set.of());

		assertThat(UserStorylineGroups.of(Set.of(5L, 82L, 49L), continuations)).extracting(Group::head)
			.containsExactly(82L, 307L, 423L);
	}

	@Test
	void aSingleChainIsOneGroupWithItsLastTopAsHead() {
		// 갈라지지 않는 1 → 2 → 3
		List<Group> groups = UserStorylineGroups.of(Set.of(1L),
				Map.of(1L, Set.of(2L, 3L), 2L, Set.of(3L), 3L, Set.of()));

		assertThat(groups).containsExactly(new Group(3, Set.of(1L, 2L, 3L), Set.of(1L)));
	}

	@Test
	void storylinesJoiningIntoOneAreOneGroup() {
		// 씨앗 1 · 9, 1 → 5 ← 9
		List<Group> groups = UserStorylineGroups.of(Set.of(1L, 9L), Map.of(1L, Set.of(5L), 9L, Set.of(5L)));

		assertThat(groups).containsExactly(new Group(5, Set.of(1L, 5L, 9L), Set.of(1L, 9L)));
	}

	@Test
	void seedsWithoutACommonHeadAreSeparateGroupsInOrderOfTheirHead() {
		// 씨앗 7(닿는 것 8) · 씨앗 2(닿는 것 없음 — 키도 없다)
		List<Group> groups = UserStorylineGroups.of(Set.of(7L, 2L), Map.of(7L, Set.of(8L)));

		assertThat(groups).containsExactly(new Group(2, Set.of(2L), Set.of(2L)), new Group(8, Set.of(7L, 8L), Set.of(7L)));
	}

	@Test
	void aSeedOnTheLaterStorylineDoesNotPullInTheEarlierStoryline() {
		// 1 → 2 인데 씨앗은 뒤 스토리라인 2 뿐 — 2 에서 닿는 것은 없다
		List<Group> groups = UserStorylineGroups.of(Set.of(2L), Map.of(2L, Set.of()));

		// 대조 — 앞 스토리라인 1 은 묶음에 없다
		assertThat(groups).containsExactly(new Group(2, Set.of(2L), Set.of(2L)));
	}

	@Test
	void aTopBeforeTwoHeadsIsInBothGroups() {
		// 씨앗 1 · 3 · 6: 1 → 2, 3 → 2 · 3 → 4, 6 → 4 — 3 은 헤드 2 와 헤드 4 의 묶음 모두에 든다
		List<Group> groups = UserStorylineGroups.of(Set.of(1L, 3L, 6L),
				Map.of(1L, Set.of(2L), 3L, Set.of(2L, 4L), 6L, Set.of(4L)));

		assertThat(groups).containsExactly(new Group(2, Set.of(1L, 2L, 3L), Set.of(1L, 3L)),
				new Group(4, Set.of(3L, 4L, 6L), Set.of(3L, 6L)));
	}

	@Test
	void aCycleWithoutAnExitEndsAtItsSmallestTop() {
		// 1 → 2 → 1 — 빠져나가는 이어짐이 없어 함께 끝이다
		List<Group> groups = UserStorylineGroups.of(Set.of(2L), Map.of(1L, Set.of(1L, 2L), 2L, Set.of(1L, 2L)));

		assertThat(groups).containsExactly(new Group(1, Set.of(1L, 2L), Set.of(2L)));
	}

	@Test
	void aCycleWithAnExitEndsAfterTheExit() {
		// 대조 — 1 → 2 → 1 이고 2 → 3
		List<Group> groups = UserStorylineGroups.of(Set.of(1L),
				Map.of(1L, Set.of(1L, 2L, 3L), 2L, Set.of(1L, 2L, 3L), 3L, Set.of()));

		assertThat(groups).containsExactly(new Group(3, Set.of(1L, 2L, 3L), Set.of(1L)));
	}

	@Test
	void noSeedsNoGroups() {
		assertThat(UserStorylineGroups.of(Set.of(), Map.of())).isEmpty();
	}

}
