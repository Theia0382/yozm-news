package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.event.domain.assignment.Reassignment.Keep;
import net.yozmnews.event.domain.assignment.Reassignment.Move;
import net.yozmnews.event.domain.assignment.Reassignment.Restamp;
import net.yozmnews.event.domain.assignment.Reassignment.Split;
import org.junit.jupiter.api.Test;

/**
 * 재판정 결과의 적용 — 유지 · 다시 적기 · 옮기기 · 떼어 내기, 사건을 연 기사는 떼어 내지 않는다. 연 사건인지는 배정의 표지로 안다.
 * <p>
 * 지키는 규칙: event-R18
 */
class ReassignmentTests {

	/** 붙었던 기사 */
	private static final boolean ATTACHED = false;

	/** 판정으로 지금 사건을 연 기사 */
	private static final boolean OPENED = true;

	@Test
	void aKeptDecisionKeepsTheAssignment() {
		assertThat(Reassignment.of(new Decision.Keep(), 10, ATTACHED, 4)).isEqualTo(new Keep());
	}

	@Test
	void attachingToTheCurrentEventRestamps() {
		assertThat(Reassignment.of(new Decision.Attach(10, 0.70, DecidedBy.FORMULA), 10, ATTACHED, 4))
			.isEqualTo(new Restamp(0.70, DecidedBy.FORMULA));
	}

	@Test
	void attachingToAnotherEventMoves() {
		assertThat(Reassignment.of(new Decision.Attach(20, 0.62, DecidedBy.EVALUATOR), 10, ATTACHED, 4))
			.isEqualTo(new Move(20, 0.62, DecidedBy.EVALUATOR));
		assertThat(Reassignment.of(new Decision.Attach(20, 0.62, DecidedBy.EVALUATOR), 10, OPENED, 4))
			.isEqualTo(new Move(20, 0.62, DecidedBy.EVALUATOR));
	}

	@Test
	void openingFromAnAttachedEventWithOthersSplits() {
		assertThat(Reassignment.of(new Decision.Open(0.70, DecidedBy.EVALUATOR), 10, ATTACHED, 2))
			.isEqualTo(new Split(0.70, DecidedBy.EVALUATOR));
	}

	@Test
	void openingFromAnAttachedEventAloneRestamps() {
		assertThat(Reassignment.of(new Decision.Open(0.70, DecidedBy.EVALUATOR), 10, ATTACHED, 1))
			.isEqualTo(new Restamp(0.70, DecidedBy.EVALUATOR));
	}

	@Test
	void theArticleThatOpenedItsEventStaysEvenWithOthersAttached() {
		assertThat(Reassignment.of(new Decision.Open(0.50, DecidedBy.FORMULA), 30, OPENED, 5))
			.isEqualTo(new Restamp(0.50, DecidedBy.FORMULA));
		assertThat(Reassignment.of(new Decision.Open(null, DecidedBy.FORMULA), 30, OPENED, 5))
			.isEqualTo(new Restamp(null, DecidedBy.FORMULA));
	}

	@Test
	void aRejudgementNeverAsks() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Reassignment.of(new Decision.Ask(List.of(20L)), 10, ATTACHED, 4));
	}

}
