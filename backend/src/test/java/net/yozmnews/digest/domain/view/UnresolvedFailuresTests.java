package net.yozmnews.digest.domain.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * 해결되지 않은 마지막 실패의 규칙 — 가장 최근 실패 뒤(ID 가 더 큰 행)에 기간을 덮는 행이 없으면 해결되지 않았다. 기대값은 손으로 적은 리터럴이다.
 * <p>
 * 지키는 규칙: digest-R40
 */
class UnresolvedFailuresTests {

	private static final FailedAttempt FAILED = new FailedAttempt(5, LocalDate.parse("2026-11-17"),
			Instant.parse("2026-11-16T22:03:00Z"));

	@Test
	void withoutAFailureNothingIsUnresolved() {
		assertThat(UnresolvedFailures.of(Optional.empty(), OptionalLong.of(3))).isEmpty();
		assertThat(UnresolvedFailures.of(Optional.empty(), OptionalLong.empty())).isEmpty();
	}

	@Test
	void aFailureAfterTheLatestCoveringRowIsUnresolved() {
		assertThat(UnresolvedFailures.of(Optional.of(FAILED), OptionalLong.of(3))).contains(FAILED);
		assertThat(UnresolvedFailures.of(Optional.of(FAILED), OptionalLong.empty())).contains(FAILED);
	}

	@Test
	void aCoveringRowAfterTheFailureResolvesIt() {
		assertThat(UnresolvedFailures.of(Optional.of(FAILED), OptionalLong.of(7))).isEmpty();
	}

}
