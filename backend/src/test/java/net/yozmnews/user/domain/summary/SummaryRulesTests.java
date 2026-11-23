package net.yozmnews.user.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 정리의 상수에서 나오는 값 — 쉬는 끝은 마지막 실패 + 연속 실패 수만큼의 날(많아야 7일), 출력 본문 토큰 상한은 글 한 벌마다 2,400 이고 관문의
 * 상한 32,000 을 넘지 않는다.
 */
class SummaryRulesTests {

	private static final Instant FAILED = Instant.parse("2026-11-20T03:00:00Z");

	@Test
	void restEndsAsManyDaysAfterTheLastFailureAsTheConsecutiveFailures() {
		assertThat(SummaryRules.restUntil(1, FAILED)).isEqualTo(Instant.parse("2026-11-21T03:00:00Z"));
		assertThat(SummaryRules.restUntil(3, FAILED)).isEqualTo(Instant.parse("2026-11-23T03:00:00Z"));
		assertThat(SummaryRules.restUntil(7, FAILED)).isEqualTo(Instant.parse("2026-11-27T03:00:00Z"));
	}

	@Test
	void restNeverExceedsSevenDays() {
		assertThat(SummaryRules.restUntil(8, FAILED)).isEqualTo(Instant.parse("2026-11-27T03:00:00Z"));
		assertThat(SummaryRules.restUntil(40, FAILED)).isEqualTo(Instant.parse("2026-11-27T03:00:00Z"));
	}

	@Test
	void withoutFailuresThereIsNoRest() {
		assertThatIllegalArgumentException().isThrownBy(() -> SummaryRules.restUntil(0, FAILED));
	}

	@Test
	void bodyTokensAreOnePartForASingleTopAndOnePerTopPlusTheWholeOtherwise() {
		assertThat(SummaryRules.bodyTokens(1)).isEqualTo(2_400);
		assertThat(SummaryRules.bodyTokens(2)).isEqualTo(7_200);
		assertThat(SummaryRules.bodyTokens(6)).isEqualTo(16_800);
	}

	@Test
	void bodyTokensStopAtTheGatewayLimit() {
		assertThat(SummaryRules.bodyTokens(12)).isEqualTo(31_200);
		assertThat(SummaryRules.bodyTokens(13)).isEqualTo(32_000);
		assertThat(SummaryRules.bodyTokens(100)).isEqualTo(32_000);
	}

	@Test
	void aFlowHasAtLeastOneTop() {
		assertThatIllegalArgumentException().isThrownBy(() -> SummaryRules.bodyTokens(0));
	}

}
