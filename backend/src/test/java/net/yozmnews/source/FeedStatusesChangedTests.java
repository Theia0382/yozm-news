package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link FeedStatusesChanged} — 바뀐 피드가 있는 회차만 이벤트다
 */
class FeedStatusesChangedTests {

	private static final Instant JUDGED_AT = Instant.parse("2026-10-30T00:00:00Z");

	private static final FeedStatusTransition DEGRADED = new FeedStatusTransition(new FeedId(11), FeedStatus.ACTIVE,
			FeedStatus.DEGRADED);

	@Test
	void keepsACopyOfTheTransitions() {
		List<FeedStatusTransition> transitions = new ArrayList<>(List.of(DEGRADED));

		FeedStatusesChanged changed = new FeedStatusesChanged(JUDGED_AT, transitions);
		transitions.clear();

		assertThat(changed.judgedAt()).isEqualTo(JUDGED_AT);
		assertThat(changed.transitions()).containsExactly(DEGRADED);
	}

	@Test
	void rejectsARunWithoutChanges() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedStatusesChanged(JUDGED_AT, List.of()));
	}

	@Test
	void rejectsMissingValues() {
		assertThatNullPointerException().isThrownBy(() -> new FeedStatusesChanged(null, List.of(DEGRADED)));
		assertThatNullPointerException().isThrownBy(() -> new FeedStatusesChanged(JUDGED_AT, null));
		assertThatNullPointerException()
			.isThrownBy(() -> new FeedStatusesChanged(JUDGED_AT, Arrays.asList(DEGRADED, null)));
	}

}
