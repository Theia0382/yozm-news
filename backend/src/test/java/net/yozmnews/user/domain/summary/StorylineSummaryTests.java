package net.yozmnews.user.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 정리 — 꼭대기의 글이 하나 이상 있고 토큰과 판은 0 이상이다.
 */
class StorylineSummaryTests {

	private static final Instant AT = Instant.parse("2026-11-20T01:00:00Z");

	private static final SummaryText TEXT = new SummaryText("제목", "요약", "최근");

	@Test
	void aSummaryWithoutAnyTopTextIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> summary(Map.of(), 0, 0, 0));
	}

	@Test
	void negativeTokensOrVersionAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> summary(Map.of(11L, TEXT), -1, 0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> summary(Map.of(11L, TEXT), 0, -1, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> summary(Map.of(11L, TEXT), 0, 0, -1));
	}

	@Test
	void oneTopTextWithZeroTokensAndNoEventsIsAccepted() {
		// 대조 — 경계값은 받는다
		StorylineSummary summary = summary(Map.of(11L, TEXT), 0, 0, 0);

		assertThat(summary.tops()).containsOnlyKeys(11L);
		assertThat(summary.events()).isEmpty();
	}

	private static StorylineSummary summary(Map<Long, SummaryText> tops, int input, int output, int version) {
		return new StorylineSummary(1, TEXT, tops, Map.of(), "ANTHROPIC", "model", input, output, AT, AT, version);
	}

}
