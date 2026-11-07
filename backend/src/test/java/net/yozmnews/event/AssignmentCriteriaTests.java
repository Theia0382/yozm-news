package net.yozmnews.event;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 기준의 범위 · 관계 — 어기면 만들 수 없다(설정이면 기동하지 않는다).
 * <p>
 * 지키는 규칙: event-R17
 */
class AssignmentCriteriaTests {

	private static final Duration DAY = Duration.ofDays(1);

	@Test
	void equalValuesAndTheEndsAreAllowed() {
		assertThatNoException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.6, 0.6, 0.6, 1));
		assertThatNoException().isThrownBy(() -> new AssignmentCriteria(Duration.ofSeconds(1), 1, 0, 0, 0.01));
	}

	@Test
	void anUpperBelowTheLowerIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.54, 0.55, 0.40, 0.50));
	}

	@Test
	void aStoreAboveTheLowerIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.80, 0.55, 0.56, 0.50));
	}

	@Test
	void valuesOutsideZeroToOneAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 1.01, 0.55, 0.40, 0.50));
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.80, 0.55, -0.01, 0.50));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssignmentCriteria(DAY, Double.NaN, 0.55, 0.40, 0.50));
	}

	@Test
	void aZeroOrTooHighProbabilityIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.80, 0.55, 0.40, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCriteria(DAY, 0.80, 0.55, 0.40, 1.01));
	}

	@Test
	void aZeroOrNegativeWindowIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssignmentCriteria(Duration.ZERO, 0.80, 0.55, 0.40, 0.50));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssignmentCriteria(Duration.ofHours(-1), 0.80, 0.55, 0.40, 0.50));
	}

	@Test
	void aWindowWithAFractionOfASecondIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AssignmentCriteria(Duration.ofMillis(72_000_500), 0.80, 0.55, 0.40, 0.50));
	}

}
