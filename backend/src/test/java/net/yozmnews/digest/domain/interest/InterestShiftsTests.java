package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 새로 생긴 관심 · 사라진 관심과 드러난 주제 — θ 0.2 둘레의 손 리터럴.
 * <p>
 * 지키는 규칙: digest-R36
 */
class InterestShiftsTests {

	@Test
	void crossingTheThresholdIsAnEntryOrALeave() {
		InterestLevels before = new InterestLevels(Map.of("up", 0.19, "down", 0.21, "stay", 0.3, "gone", 0.5));
		InterestLevels after = new InterestLevels(Map.of("up", 0.21, "down", 0.19, "stay", 0.3, "fresh", 0.25));

		InterestShifts.Shift shift = InterestShifts.between(before, after);

		assertThat(shift.entered()).containsExactlyInAnyOrder("up", "fresh");
		assertThat(shift.left()).containsExactlyInAnyOrder("down", "gone");
	}

	@Test
	void noInterestOnEitherSideIsNoShift() {
		InterestShifts.Shift shift = InterestShifts.between(InterestLevels.NONE,
				new InterestLevels(Map.of("weak", 0.1, "disliked", -0.6)));

		assertThat(shift.entered()).isEmpty();
		assertThat(shift.left()).isEmpty();
	}

	@Test
	void prominentTopicsReachTheThresholdOnEitherSideInSomePeriod() {
		List<InterestLevels> periods = List.of(new InterestLevels(Map.of("a", 0.1, "b", -0.25)),
				new InterestLevels(Map.of("c", 0.2, "d", -0.19, "a", 0.15)));

		assertThat(InterestShifts.prominent(periods)).containsExactlyInAnyOrder("b", "c");
		assertThat(InterestShifts.prominent(List.of())).isEmpty();
	}

}
