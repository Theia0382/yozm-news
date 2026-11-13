package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * {@link FeedStatusTransition} — 바뀐 상태만 전이다
 */
class FeedStatusTransitionTests {

	@Test
	void acceptsAChangedStatus() {
		FeedStatusTransition transition = new FeedStatusTransition(new FeedId(11), FeedStatus.ACTIVE,
				FeedStatus.DEGRADED);

		assertThat(transition.feedId()).isEqualTo(new FeedId(11));
		assertThat(transition.from()).isEqualTo(FeedStatus.ACTIVE);
		assertThat(transition.to()).isEqualTo(FeedStatus.DEGRADED);
	}

	@Test
	void rejectsTheSameStatus() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FeedStatusTransition(new FeedId(11), FeedStatus.STALE, FeedStatus.STALE));
	}

	@Test
	void rejectsMissingValues() {
		assertThatNullPointerException()
			.isThrownBy(() -> new FeedStatusTransition(null, FeedStatus.ACTIVE, FeedStatus.STALE));
		assertThatNullPointerException()
			.isThrownBy(() -> new FeedStatusTransition(new FeedId(11), null, FeedStatus.STALE));
		assertThatNullPointerException()
			.isThrownBy(() -> new FeedStatusTransition(new FeedId(11), FeedStatus.ACTIVE, null));
	}

}
