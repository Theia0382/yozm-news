package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 같은 사건의 저장 후보 모으기 — 합쳐진 사건을 남는 사건으로 읽으면 같은 사건이 둘이 된다.
 * <p>
 * 지키는 규칙: event-R18
 */
class StoredCandidateTests {

	@Test
	void theSameEventTakesTheHigherScoreAndTheHigherProbability() {
		assertThat(StoredCandidate.folded(List.of(new StoredCandidate(7, 0.60, null), new StoredCandidate(7, 0.62, 0.9))))
			.containsExactly(new StoredCandidate(7, 0.62, 0.9));
		assertThat(StoredCandidate.folded(List.of(new StoredCandidate(7, 0.70, 0.3), new StoredCandidate(7, 0.50, null))))
			.containsExactly(new StoredCandidate(7, 0.70, 0.3));
		assertThat(StoredCandidate.folded(List.of(new StoredCandidate(7, 0.55, 0.8), new StoredCandidate(7, 0.65, 0.4))))
			.containsExactly(new StoredCandidate(7, 0.65, 0.8));
	}

	@Test
	void differentEventsStayInEventOrder() {
		assertThat(StoredCandidate.folded(List.of(new StoredCandidate(9, 0.5, null), new StoredCandidate(3, 0.6, 0.2))))
			.containsExactly(new StoredCandidate(3, 0.6, 0.2), new StoredCandidate(9, 0.5, null));
		assertThat(StoredCandidate.folded(List.of())).isEmpty();
	}

}
