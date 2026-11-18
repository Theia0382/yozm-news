package net.yozmnews.user.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import net.yozmnews.tag.TagSetId;
import org.junit.jupiter.api.Test;

/**
 * 같은 활성 주제에 닿는 두 거절 가운데 남길 것 — 늦은 거절, 같은 시각이면 관심이 큰 거절. 관심은 −1 ~ 1.
 * <p>
 * 지키는 규칙: user-R65
 */
class RecordedDeclineTests {

	private static final TagSetId V1 = new TagSetId(1);

	private static final TagSetId V2 = new TagSetId(2);

	private static final Instant EARLY = Instant.parse("2026-09-01T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-10-01T00:00:00Z");

	@Test
	void theLaterDeclineWinsWhicheverComesFirst() {
		RecordedDecline early = new RecordedDecline(V1, "economy.a", 0.9, EARLY);
		RecordedDecline late = new RecordedDecline(V2, "economy.b", 0.3, LATE);

		assertThat(RecordedDecline.later(early, late)).isSameAs(late);
		assertThat(RecordedDecline.later(late, early)).isSameAs(late);
	}

	@Test
	void atTheSameTimeTheHigherLevelWins() {
		RecordedDecline low = new RecordedDecline(V1, "economy.a", 0.3, LATE);
		RecordedDecline high = new RecordedDecline(V2, "economy.b", 0.6, LATE);

		assertThat(RecordedDecline.later(low, high)).isSameAs(high);
		assertThat(RecordedDecline.later(high, low)).isSameAs(high);
	}

	@Test
	void aLevelOutsideMinusOneToOneIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RecordedDecline(V1, "economy.a", 1.0001, LATE));
		assertThatIllegalArgumentException().isThrownBy(() -> new RecordedDecline(V1, "economy.a", -1.0001, LATE));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RecordedDecline(V1, "economy.a", Double.NaN, LATE));
		assertThat(new RecordedDecline(V1, "economy.a", 1.0, LATE).level()).isEqualTo(1.0);
		assertThat(new RecordedDecline(V1, "economy.a", -1.0, LATE).level()).isEqualTo(-1.0);
	}

}
