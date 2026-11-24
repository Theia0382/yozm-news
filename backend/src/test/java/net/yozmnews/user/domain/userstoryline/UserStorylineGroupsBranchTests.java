package net.yozmnews.user.domain.userstoryline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.user.domain.userstoryline.UserStorylineGroups.Group;
import org.junit.jupiter.api.Test;

/**
 * 갈라지는 흐름의 묶음 — 갈래마다 묶음이고 갈라지기 전 꼭대기는 갈래 모두에 든다(docs/specs/user.md §5). 묶음의 추적 대상과 씨앗만 본다.
 */
class UserStorylineGroupsBranchTests {

	@Test
	void eachHeadOfAMemberUsersFlowIsItsOwnGroup() {
		// 측정 사용자 u1 의 관계: 5 → 82 · 5 → 307 · 5 → 49 · 5 → 423 · 49 → 423, 씨앗 5 · 82 · 49
		Map<Long, Set<Long>> continuations = Map.of(5L, Set.of(82L, 307L, 49L, 423L), 49L, Set.of(423L), 82L, Set.of(),
				307L, Set.of(), 423L, Set.of());

		List<Group> groups = UserStorylineGroups.of(Set.of(5L, 82L, 49L), continuations);

		assertThat(groups).extracting(Group::tops)
			.containsExactly(Set.of(5L, 82L), Set.of(5L, 307L), Set.of(5L, 49L, 423L));
		assertThat(groups).extracting(Group::seedTops)
			.containsExactly(Set.of(5L, 82L), Set.of(5L), Set.of(5L, 49L));
	}

	@Test
	void aSingleChainStaysOneGroup() {
		// 대조 — 갈라지지 않는 5 → 49 → 423
		List<Group> groups = UserStorylineGroups.of(Set.of(5L, 49L),
				Map.of(5L, Set.of(49L, 423L), 49L, Set.of(423L), 423L, Set.of()));

		assertThat(groups).extracting(Group::tops).containsExactly(Set.of(5L, 49L, 423L));
		assertThat(groups).extracting(Group::seedTops).containsExactly(Set.of(5L, 49L));
	}

}
