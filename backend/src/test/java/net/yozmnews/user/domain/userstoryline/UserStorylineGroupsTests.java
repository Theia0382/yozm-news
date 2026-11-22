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
 * 지키는 규칙: 추적 대상이 겹치는 씨앗은 한 묶음이다 — 갈라지는 가지도, 한 흐름으로 모이는 흐름들도(docs/specs/user.md §5)
 */
class UserStorylineGroupsTests {

	@Test
	void aBranchingFlowIsOneGroup() {
		// 씨앗 1, 1 → 2 · 1 → 3
		List<Group> groups = UserStorylineGroups.of(Set.of(1L), Map.of(1L, Set.of(2L, 3L)));

		assertThat(groups).containsExactly(new Group(Set.of(1L, 2L, 3L), Set.of(1L)));
	}

	@Test
	void flowsJoiningIntoOneAreOneGroup() {
		// 씨앗 1 · 9, 1 → 5 ← 9
		List<Group> groups = UserStorylineGroups.of(Set.of(1L, 9L), Map.of(1L, Set.of(5L), 9L, Set.of(5L)));

		assertThat(groups).containsExactly(new Group(Set.of(1L, 5L, 9L), Set.of(1L, 9L)));
	}

	@Test
	void seedsWithoutACommonTopAreSeparateGroupsInOrderOfTheirSmallestTop() {
		// 씨앗 7(닿는 것 8) · 씨앗 2(닿는 것 없음 — 키도 없다)
		List<Group> groups = UserStorylineGroups.of(Set.of(7L, 2L), Map.of(7L, Set.of(8L)));

		assertThat(groups).containsExactly(new Group(Set.of(2L), Set.of(2L)), new Group(Set.of(7L, 8L), Set.of(7L)));
	}

	@Test
	void aSeedOnTheLaterFlowDoesNotPullInTheEarlierFlow() {
		// 1 → 2 인데 씨앗은 뒤 흐름 2 뿐 — 2 에서 닿는 것은 없다
		List<Group> groups = UserStorylineGroups.of(Set.of(2L), Map.of(2L, Set.of()));

		// 대조 — 앞 흐름 1 은 묶음에 없다
		assertThat(groups).containsExactly(new Group(Set.of(2L), Set.of(2L)));
	}

	@Test
	void overlapsAreFollowedTransitively() {
		// 씨앗 1 · 3 · 6: 1 → 2, 3 → 2 · 3 → 4, 6 → 4 — 1 과 6 은 3 을 거쳐 한 묶음이다
		List<Group> groups = UserStorylineGroups.of(Set.of(1L, 3L, 6L),
				Map.of(1L, Set.of(2L), 3L, Set.of(2L, 4L), 6L, Set.of(4L)));

		assertThat(groups).containsExactly(new Group(Set.of(1L, 2L, 3L, 4L, 6L), Set.of(1L, 3L, 6L)));
	}

	@Test
	void noSeedsNoGroups() {
		assertThat(UserStorylineGroups.of(Set.of(), Map.of())).isEmpty();
	}

}
