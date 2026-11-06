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
 * 재판정 결과의 적용 — 유지 · 다시 적기 · 옮기기 · 떼어 내기, 사건을 연 기사는 떼어 내지 않는다.
 */
class ReassignmentTests {

	/** 기사가 지금 배정된 사건 10 은 저장 후보다(붙었던 사건) */
	private static final List<StoredCandidate> ATTACHED_TO_10 = List.of(new StoredCandidate(10, 0.70, 0.60),
			new StoredCandidate(20, 0.62, null));

	/** 지금 사건 30 은 저장 후보에 없다 — 그 기사가 판정 때 연 사건 */
	private static final List<StoredCandidate> OPENED_30 = List.of(new StoredCandidate(10, 0.50, null));

	@Test
	void aKeptDecisionKeepsTheAssignment() {
		assertThat(Reassignment.of(new Decision.Keep(), 10, ATTACHED_TO_10, 4)).isEqualTo(new Keep());
	}

	@Test
	void attachingToTheCurrentEventRestamps() {
		assertThat(Reassignment.of(new Decision.Attach(10, 0.70, DecidedBy.FORMULA), 10, ATTACHED_TO_10, 4))
			.isEqualTo(new Restamp(0.70, DecidedBy.FORMULA));
	}

	@Test
	void attachingToAnotherEventMoves() {
		assertThat(Reassignment.of(new Decision.Attach(20, 0.62, DecidedBy.EVALUATOR), 10, ATTACHED_TO_10, 4))
			.isEqualTo(new Move(20, 0.62, DecidedBy.EVALUATOR));
	}

	@Test
	void openingFromAnAttachedEventWithOthersSplits() {
		assertThat(Reassignment.of(new Decision.Open(0.70, DecidedBy.EVALUATOR), 10, ATTACHED_TO_10, 2))
			.isEqualTo(new Split(0.70, DecidedBy.EVALUATOR));
	}

	@Test
	void openingFromAnAttachedEventAloneRestamps() {
		assertThat(Reassignment.of(new Decision.Open(0.70, DecidedBy.EVALUATOR), 10, ATTACHED_TO_10, 1))
			.isEqualTo(new Restamp(0.70, DecidedBy.EVALUATOR));
	}

	@Test
	void theArticleThatOpenedItsEventStaysEvenWithOthersAttached() {
		assertThat(Reassignment.of(new Decision.Open(0.50, DecidedBy.FORMULA), 30, OPENED_30, 5))
			.isEqualTo(new Restamp(0.50, DecidedBy.FORMULA));
		assertThat(Reassignment.of(new Decision.Open(null, DecidedBy.FORMULA), 30, List.of(), 5))
			.isEqualTo(new Restamp(null, DecidedBy.FORMULA));
	}

	@Test
	void aRejudgementNeverAsks() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Reassignment.of(new Decision.Ask(List.of(20L)), 10, ATTACHED_TO_10, 4));
	}

}
