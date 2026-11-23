package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 관심사 히스토리에서 흐름이 보이고 사라지는 판정 — 관심이 만들 기준 0.35 이상이면 보이게 되고, 보이던 꼭대기는 남길 기준 0.2 이상인 동안
 * 남으며, 그 아래로 내려가면 사라진다. 두 경계는 포함이다.
 * <p>
 * 지키는 규칙: digest-R36 · digest-R51
 */
class StorylineShiftsTests {

	private static final StorylineId A = new StorylineId(1);

	private static final StorylineId B = new StorylineId(2);

	private static final StorylineId C = new StorylineId(3);

	@Test
	void aTopAppearsWhenItsInterestReachesTheFollowThreshold() {
		assertThat(StorylineShifts.between(Set.of(), levels(Map.of(A, 0.35, B, 0.34))))
			.isEqualTo(new StorylineShifts.Shift(Set.of(A), Set.of(A), Set.of()));
	}

	@Test
	void aShownTopStaysWhileItsInterestIsAtOrAboveTheKeepThreshold() {
		assertThat(StorylineShifts.between(Set.of(A, B), levels(Map.of(A, 0.2, B, 0.34))))
			.isEqualTo(new StorylineShifts.Shift(Set.of(A, B), Set.of(), Set.of()));
	}

	@Test
	void aShownTopLeavesBelowTheKeepThresholdOrWithoutSignals() {
		assertThat(StorylineShifts.between(Set.of(A, B, C), levels(Map.of(A, 0.19, C, 0.5))))
			.isEqualTo(new StorylineShifts.Shift(Set.of(C), Set.of(), Set.of(A, B)));
	}

	@Test
	void aTopBetweenTheThresholdsThatWasNotShownDoesNotAppear() {
		assertThat(StorylineShifts.between(Set.of(), levels(Map.of(A, 0.2, B, 0.3))))
			.isEqualTo(new StorylineShifts.Shift(Set.of(), Set.of(), Set.of()));
	}

	@Test
	void appearingAndLeavingAreJudgedTopByTop() {
		assertThat(StorylineShifts.between(Set.of(A, B), levels(Map.of(A, 0.1, B, 0.25, C, 0.9))))
			.isEqualTo(new StorylineShifts.Shift(Set.of(B, C), Set.of(C), Set.of(A)));
	}

	@Test
	void theFirstReferenceShowsOnlyTopsAtOrAboveTheFollowThreshold() {
		assertThat(StorylineShifts.first(levels(Map.of(A, 0.35, B, 0.3, C, -0.5)))).containsExactly(A);
		assertThat(StorylineShifts.first(StorylineLevels.NONE)).isEmpty();
	}

	private static StorylineLevels levels(Map<StorylineId, Double> levels) {
		return new StorylineLevels(levels);
	}

}
