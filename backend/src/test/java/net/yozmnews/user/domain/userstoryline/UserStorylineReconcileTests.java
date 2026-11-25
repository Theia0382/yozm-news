package net.yozmnews.user.domain.userstoryline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.user.domain.userstoryline.UserStorylineGroups.Group;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Created;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Kept;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile.Lost;
import org.junit.jupiter.api.Test;

/**
 * 저장한 사용자 스토리라인을 지금의 묶음에 잇기.
 * <p>
 * 지키는 규칙: 잇기의 평가 순서 — 저장 헤드로 고르기(없으면 추적 씨앗 · 겹침) · 합쳐짐 · 새 개체 · 추적 씨앗을 그 꼭대기를 가진 묶음 모두로 ·
 * 저장 헤드를 가진 묶음의 물려받기 · 사라짐, 추적 중인 것과 추적 씨앗이 하나 이상인 것은 같다(docs/specs/user.md §5)
 */
class UserStorylineReconcileTests {

	private static final Instant CREATED = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant EARLY = Instant.parse("2026-11-03T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-11-09T00:00:00Z");

	@Test
	void aPieceOnTheHeadSideKeepsTheStorylineAndInheritsTheTracking() {
		// 저장 U10{1, 2} — 헤드 2 · 추적 중 · 추적 씨앗 1. 1 → 2 가 사라져 묶음이 {1} · {2} 로 갈렸다
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(1L, 1L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(1, Set.of(1L), Set.of(1L)), new Group(2, Set.of(2L), Set.of(2L))));

		// 헤드 쪽 묶음을 잇고 추적 씨앗에서 떨어졌으니 그 묶음의 씨앗 꼭대기를 물려받는다
		assertThat(result.kept()).containsExactly(new Kept(10, 2, Set.of(2L), Map.of(2L, 2L), Set.of(2L), EARLY));
		// 추적 씨앗은 제 꼭대기의 묶음으로 간다 — 그 묶음은 새 개체로 추적 중이다
		assertThat(result.created()).containsExactly(new Created(1, Set.of(1L), Map.of(1L, 1L), Set.of(), EARLY));
		assertThat(result.removed()).isEmpty();
	}

	@Test
	void aSplitPieceOfAnUntrackedStorylineIsNotTracked() {
		// 대조 — 추적 중이 아니던 개체가 갈리면 조각은 물려받지 않는다
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(), null);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(1, Set.of(1L), Set.of(1L)), new Group(2, Set.of(2L), Set.of(2L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 2, Set.of(2L), Map.of(), Set.of(), null));
		assertThat(result.created()).containsExactly(new Created(1, Set.of(1L), Map.of(), Set.of(), null));
	}

	@Test
	void trackingTheBranchBeforeAForkTracksOnlyThatBranch() {
		// 측정 사용자 u1 — 헤드 열 전에 저장된 개체 {5, 49, 82, 307, 423}(헤드 없음)이 추적 씨앗 82 로 추적 중이다.
		// 지금 묶음은 헤드 82 · 307 · 423 의 셋이고 5 는 셋 모두에 든다
		StoredUserStoryline stored = stored(10, null, Set.of(5L, 49L, 82L, 307L, 423L), Map.of(82L, 82L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored), u1Groups());

		// 추적 씨앗을 가진 묶음을 잇고, 헤드가 없던 개체는 물려받기를 하지 않는다 — 형제 갈래는 추적 중이 아니다
		assertThat(result.kept()).containsExactly(new Kept(10, 82, Set.of(5L, 82L), Map.of(82L, 82L), Set.of(), EARLY));
		assertThat(result.created()).containsExactly(new Created(307, Set.of(5L, 307L), Map.of(), Set.of(), null),
				new Created(423, Set.of(5L, 49L, 423L), Map.of(), Set.of(), null));
		assertThat(result.removed()).isEmpty();
	}

	@Test
	void trackingAnotherBranchTracksThatBranchInstead() {
		// 대조 — 같은 개체의 추적 씨앗이 49 면 헤드 423 의 묶음만 추적 중이다
		StoredUserStoryline stored = stored(10, null, Set.of(5L, 49L, 82L, 307L, 423L), Map.of(49L, 49L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored), u1Groups());

		assertThat(result.kept())
			.containsExactly(new Kept(10, 423, Set.of(5L, 49L, 423L), Map.of(49L, 49L), Set.of(), EARLY));
		assertThat(result.created()).containsExactly(new Created(82, Set.of(5L, 82L), Map.of(), Set.of(), null),
				new Created(307, Set.of(5L, 307L), Map.of(), Set.of(), null));
	}

	@Test
	void aSiblingBranchSharingOnlyTheTopBeforeTheForkDoesNotInherit() {
		// 저장 U10{5, 82} — 헤드 82 · 추적 씨앗 82. 5 에 반응이 있어 헤드 307 의 묶음 {5, 307} 이 함께 생겼다
		StoredUserStoryline stored = stored(10, 82L, Set.of(5L, 82L), Map.of(82L, 82L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(82, Set.of(5L, 82L), Set.of(5L, 82L)), new Group(307, Set.of(5L, 307L), Set.of(5L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 82, Set.of(5L, 82L), Map.of(82L, 82L), Set.of(), EARLY));
		// 저장 헤드 82 를 갖지 않으니 추적을 물려받지 않는다
		assertThat(result.created()).containsExactly(new Created(307, Set.of(5L, 307L), Map.of(), Set.of(), null));
	}

	@Test
	void theHeadMovesForwardWithANewContinuation() {
		// 저장 U10{1} — 헤드 1 · 추적 씨앗 11(꼭대기 1). 1 → 2 가 생겼다
		StoredUserStoryline stored = stored(10, 1L, Set.of(1L), Map.of(11L, 1L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(2, Set.of(1L, 2L), Set.of(1L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 2, Set.of(1L, 2L), Map.of(11L, 1L), Set.of(), EARLY));
	}

	@Test
	void aForkAfterTheTrackedTopKeepsTheSmallestHeadAndTracksEveryBranch() {
		// 저장 U10{1} — 헤드 1 · 추적 씨앗 1. 1 → 2 · 1 → 3 으로 갈라졌다
		StoredUserStoryline stored = stored(10, 1L, Set.of(1L), Map.of(1L, 1L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(2, Set.of(1L, 2L), Set.of(1L)), new Group(3, Set.of(1L, 3L), Set.of(1L))));

		// 저장 헤드를 가진 묶음 가운데 헤드가 가장 작은 것을 잇는다. 추적 씨앗 1 은 두 갈래 모두에 든다
		assertThat(result.kept()).containsExactly(new Kept(10, 2, Set.of(1L, 2L), Map.of(1L, 1L), Set.of(), EARLY));
		assertThat(result.created()).containsExactly(new Created(3, Set.of(1L, 3L), Map.of(1L, 1L), Set.of(), EARLY));
	}

	@Test
	void aForkAtTheHeadGivesTheOtherBranchTheTrackingByInheritance() {
		// 저장 U10{1, 2} — 헤드 2 · 추적 씨앗 1. 1 → 2 는 사라지고 2 → 3 · 2 → 4 가 생겨 2 에서 갈라졌다(씨앗 1 은 따로)
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(1L, 1L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(1, Set.of(1L), Set.of(1L)), new Group(3, Set.of(2L, 3L), Set.of(2L)),
						new Group(4, Set.of(2L, 4L), Set.of(2L))));

		// 헤드 2 를 가진 갈래 둘 가운데 헤드가 작은 3 을 잇고, 두 갈래 모두 저장 헤드를 가져 물려받는다
		assertThat(result.kept()).containsExactly(new Kept(10, 3, Set.of(2L, 3L), Map.of(2L, 2L), Set.of(2L), EARLY));
		assertThat(result.created()).containsExactly(new Created(1, Set.of(1L), Map.of(1L, 1L), Set.of(), EARLY),
				new Created(4, Set.of(2L, 4L), Map.of(2L, 2L), Set.of(2L), EARLY));
	}

	@Test
	void anUntrackedStorylineWithoutAHeadChoosesTheMostOverlapThenTheSmallestHead() {
		// 헤드 열 전에 저장된 추적 중이 아닌 개체 {5, 82, 307} — 두 묶음과 둘씩 겹친다
		StoredUserStoryline stored = stored(10, null, Set.of(5L, 82L, 307L), Map.of(), null);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(82, Set.of(5L, 82L), Set.of(5L, 82L)), new Group(307, Set.of(5L, 307L), Set.of(5L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 82, Set.of(5L, 82L), Map.of(), Set.of(), null));
		assertThat(result.created()).containsExactly(new Created(307, Set.of(5L, 307L), Map.of(), Set.of(), null));
	}

	@Test
	void aStorylineWhoseHeadLeftEveryGroupFollowsItsTrackedSeedAndStaysTracked() {
		// 저장 U10{1, 2} — 헤드 2 · 추적 씨앗 1. 1 → 2 가 사라졌고 2 에는 신호가 없어 묶음은 {1} 하나다
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(1L, 1L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(1, Set.of(1L), Set.of(1L))));

		// 사라지지 않는다 — 사라진 흐름 추적 알림의 근거(추적 중이던 것이 이을 묶음 없이 사라짐)가 생기지 않는다
		assertThat(result.kept()).containsExactly(new Kept(10, 1, Set.of(1L), Map.of(1L, 1L), Set.of(), EARLY));
		assertThat(result.removed()).isEmpty();
	}

	@Test
	void storylinesMergingIntoOneGroupKeepTheSmallestIdAndTheTracking() {
		// 저장 U10{1} · U20{9}(추적 중 — 씨앗 9). 1 → 5 ← 9 로 묶음 하나가 됐다
		StoredUserStoryline first = stored(10, 1L, Set.of(1L), Map.of(), null);
		StoredUserStoryline second = stored(20, 9L, Set.of(9L), Map.of(9L, 9L), LATE);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(second, first),
				List.of(new Group(5, Set.of(1L, 5L, 9L), Set.of(1L, 9L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 5, Set.of(1L, 5L, 9L), Map.of(9L, 9L), Set.of(), LATE));
		assertThat(result.created()).isEmpty();
		assertThat(result.removed()).containsExactly(new Lost(20, true, 10L));
	}

	@Test
	void mergedTrackingsKeepTheEarliestTime() {
		StoredUserStoryline first = stored(10, 1L, Set.of(1L), Map.of(1L, 1L), LATE);
		StoredUserStoryline second = stored(20, 9L, Set.of(9L), Map.of(9L, 9L), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(first, second),
				List.of(new Group(5, Set.of(1L, 5L, 9L), Set.of(1L, 9L))));

		assertThat(result.kept())
			.containsExactly(new Kept(10, 5, Set.of(1L, 5L, 9L), Map.of(1L, 1L, 9L, 9L), Set.of(), EARLY));
		assertThat(result.removed()).containsExactly(new Lost(20, true, 10L));
	}

	@Test
	void aStorylineWithoutAnyGroupIsLostAndAGroupWithoutAnyStorylineIsCreated() {
		// 저장 U10{1} 은 신호가 창 밖으로 나가 묶음이 없고, 묶음 {7, 8} 은 새 신호다
		StoredUserStoryline stored = stored(10, 1L, Set.of(1L), Map.of(), null);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(8, Set.of(7L, 8L), Set.of(7L))));

		assertThat(result.kept()).isEmpty();
		assertThat(result.created()).containsExactly(new Created(8, Set.of(7L, 8L), Map.of(), Set.of(), null));
		assertThat(result.removed()).containsExactly(new Lost(10, false, null));
	}

	@Test
	void aTrackedStorylineWithoutSeedsAndWithoutAnyGroupIsLostAsTracked() {
		// 추적 중인데 추적 씨앗의 스토리라인이 모두 지워졌고(씨앗 없음) 스토리라인의 꼭대기에도 신호가 없어 이을 묶음이 없다
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(7, Set.of(7L), Set.of(7L))));

		// 합쳐진 것이 아니라 사라진 것이다 — 추적 중이었다는 것이 남는다. 저장 헤드를 갖지 않은 묶음은 추적을 물려받지 않는다
		assertThat(result.kept()).isEmpty();
		assertThat(result.created()).containsExactly(new Created(7, Set.of(7L), Map.of(), Set.of(), null));
		assertThat(result.removed()).containsExactly(new Lost(10, true, null));
	}

	@Test
	void manyToManyIsDecidedByEachStorylinesHead() {
		// 저장 U10{1, 2, 3}(헤드 3 · 추적 중 — 씨앗 1) · U20{4}(헤드 4), 묶음 G1{1, 4}(헤드 4) · G2{2, 3}(헤드 3)
		StoredUserStoryline first = stored(10, 3L, Set.of(1L, 2L, 3L), Map.of(1L, 1L), EARLY);
		StoredUserStoryline second = stored(20, 4L, Set.of(4L), Map.of(), null);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(first, second),
				List.of(new Group(4, Set.of(1L, 4L), Set.of(1L, 4L)), new Group(3, Set.of(2L, 3L), Set.of(2L))));

		// U10 은 헤드 3 의 G2 를, U20 은 헤드 4 의 G1 을 잇는다. 추적 씨앗 1 은 G1 로 가 U20 이 추적 중이 되고,
		// U10 은 G2 의 씨앗 꼭대기 2 를 물려받아 추적 중으로 남는다
		assertThat(result.kept()).containsExactly(new Kept(10, 3, Set.of(2L, 3L), Map.of(2L, 2L), Set.of(2L), EARLY),
				new Kept(20, 4, Set.of(1L, 4L), Map.of(1L, 1L), Set.of(), EARLY));
		assertThat(result.created()).isEmpty();
		assertThat(result.removed()).isEmpty();
	}

	@Test
	void aTrackedStorylineWhoseSeedsWereAllDeletedKeepsTrackingTheRemainingUserStoryline() {
		// 추적 중인데 추적 씨앗의 스토리라인이 모두 지워졌다(씨앗 없음). 헤드 2 에 신호가 남아 있다
		StoredUserStoryline stored = stored(10, 2L, Set.of(1L, 2L), Map.of(), EARLY);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(stored),
				List.of(new Group(2, Set.of(2L), Set.of(2L))));

		assertThat(result.kept()).containsExactly(new Kept(10, 2, Set.of(2L), Map.of(2L, 2L), Set.of(2L), EARLY));
		assertThat(result.removed()).isEmpty();
	}

	@Test
	void trackedMeansAtLeastOneTrackedSeedInEveryResult() {
		StoredUserStoryline first = stored(10, 3L, Set.of(1L, 2L, 3L), Map.of(1L, 1L), EARLY);
		StoredUserStoryline second = stored(20, 4L, Set.of(4L), Map.of(), null);

		UserStorylineReconcile result = UserStorylineReconcile.decide(List.of(first, second),
				List.of(new Group(4, Set.of(1L, 4L), Set.of(1L, 4L)), new Group(2, Set.of(2L), Set.of(2L)),
						new Group(3, Set.of(3L), Set.of(3L)), new Group(8, Set.of(8L), Set.of(8L))));

		// U10 이 {3} 을, U20 이 {1, 4} 를 잇고 나머지 묶음 둘은 새 개체다 — 아래 단언이 빈 목록에서 통과하지 않게 한다
		assertThat(result.kept()).hasSize(2);
		assertThat(result.created()).hasSize(2);
		assertThat(result.kept()).allSatisfy(
				one -> assertThat(one.subscribedAt() != null).isEqualTo(!one.trackedSeeds().isEmpty()));
		assertThat(result.created()).allSatisfy(
				one -> assertThat(one.subscribedAt() != null).isEqualTo(!one.trackedSeeds().isEmpty()));
		// 관계없는 새 묶음 {8} 과 저장 헤드를 갖지 않은 {2} 는 추적 중이 아니다
		assertThat(result.created()).containsExactly(new Created(2, Set.of(2L), Map.of(), Set.of(), null),
				new Created(8, Set.of(8L), Map.of(), Set.of(), null));
	}

	/** 측정 사용자 u1 의 묶음 — 5 → 82 · 5 → 307 · 5 → 49 · 5 → 423 · 49 → 423, 씨앗 5 · 82 · 49 */
	private static List<Group> u1Groups() {
		return List.of(new Group(82, Set.of(5L, 82L), Set.of(5L, 82L)), new Group(307, Set.of(5L, 307L), Set.of(5L)),
				new Group(423, Set.of(5L, 49L, 423L), Set.of(5L, 49L)));
	}

	private static StoredUserStoryline stored(long id, Long head, Set<Long> tops, Map<Long, Long> seeds,
			Instant subscribedAt) {
		return new StoredUserStoryline(id, tops, seeds, subscribedAt, CREATED, head);
	}

}
