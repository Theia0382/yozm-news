package net.yozmnews.user.domain.userstoryline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 추적 중인 사용자 스토리라인 일부를 끌 때의 추적 씨앗.
 * <p>
 * 지키는 규칙: 끄는 쪽과 남는 쪽이 꼭대기를 함께 가진 추적 씨앗은 양쪽 모두 각자의 헤드로 바꾸고, 사용자의 해제는 바꾼 뒤 끄는 쪽의 씨앗이다
 * (docs/specs/user.md §5 user-R52)
 */
class BranchUntrackingTests {

	private static final Instant AT = Instant.parse("2026-11-09T00:00:00Z");

	/** 갈라지기 전 5 를 눌러 갈래 셋(헤드 82 · 307 · 423)이 모두 씨앗 5 로 추적 중 */
	private static final List<StoredUserStoryline> FORK = List.of(tracked(1, 82, Set.of(5L, 82L), Map.of(5L, 5L)),
			tracked(2, 307, Set.of(5L, 307L), Map.of(5L, 5L)), tracked(3, 423, Set.of(5L, 49L, 423L), Map.of(5L, 5L)));

	@Test
	void untrackingOneBranchMovesTheSharedSeedToEachHead() {
		BranchUntracking result = BranchUntracking.decide(FORK, Set.of(1L));

		// 남는 갈래는 제 헤드가 씨앗이다 — 씨앗 5 가 남지 않아 끈 갈래로 돌아가지 않는다
		assertThat(result.seeds()).isEqualTo(Map.of(2L, Set.of(307L), 3L, Set.of(423L)));
		assertThat(result.splitRemoved()).containsExactly(5L);
		assertThat(result.splitAdded()).containsExactlyInAnyOrder(82L, 307L, 423L);
		// 사용자의 해제는 끈 갈래의 헤드 하나 — 남는 갈래의 관심에 뺌이 들지 않는다
		assertThat(result.userRemoved()).containsExactly(82L);
	}

	@Test
	void untrackingEveryBranchMovesNothing() {
		// 대조 — 갈라지기 전 꼭대기에서 끄면 갈래 모두가 끄는 쪽이라 남는 쪽이 없다
		BranchUntracking result = BranchUntracking.decide(FORK, Set.of(1L, 2L, 3L));

		assertThat(result.seeds()).isEmpty();
		assertThat(result.splitAdded()).isEmpty();
		assertThat(result.splitRemoved()).isEmpty();
		assertThat(result.userRemoved()).containsExactly(5L);
	}

	@Test
	void branchesTrackedByTheirOwnSeedsKeepThem() {
		// 대조 — 갈래마다 제 스토리라인을 눌러 추적했으면 함께 가진 씨앗이 없다
		List<StoredUserStoryline> own = List.of(tracked(1, 82, Set.of(5L, 82L), Map.of(82L, 82L)),
				tracked(2, 307, Set.of(5L, 307L), Map.of(307L, 307L)));

		BranchUntracking result = BranchUntracking.decide(own, Set.of(1L));

		assertThat(result.seeds()).isEmpty();
		assertThat(result.splitAdded()).isEmpty();
		assertThat(result.splitRemoved()).isEmpty();
		assertThat(result.userRemoved()).containsExactly(82L);
	}

	@Test
	void aSeedOnlyTheLeavingBranchHasIsRemovedAsItIs() {
		// 끄는 갈래가 공유 씨앗 5 와 제 씨앗 82 를 함께 가졌다 — 5 는 헤드 82 로 바뀌어 하나로 합쳐진다
		List<StoredUserStoryline> mixed = List.of(tracked(1, 82, Set.of(5L, 82L), Map.of(5L, 5L, 82L, 82L)),
				tracked(2, 307, Set.of(5L, 307L), Map.of(5L, 5L)));

		BranchUntracking result = BranchUntracking.decide(mixed, Set.of(1L));

		assertThat(result.seeds()).isEqualTo(Map.of(2L, Set.of(307L)));
		assertThat(result.splitRemoved()).containsExactly(5L);
		assertThat(result.splitAdded()).containsExactly(307L);
		assertThat(result.userRemoved()).containsExactly(82L);
	}

	private static StoredUserStoryline tracked(long id, long head, Set<Long> tops, Map<Long, Long> seeds) {
		return new StoredUserStoryline(id, tops, seeds, AT, AT, head);
	}

}
