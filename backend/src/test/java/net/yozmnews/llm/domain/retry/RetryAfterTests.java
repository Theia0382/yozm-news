package net.yozmnews.llm.domain.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * {@code retry-after} 헤더 — 0 이상의 정수 초만 읽는다.
 * <p>
 * 지키는 규칙: llm-R20
 */
class RetryAfterTests {

	@Test
	void readsWholeSeconds() {
		assertThat(RetryAfter.parse("30")).contains(Duration.ofSeconds(30));
		assertThat(RetryAfter.parse("0")).contains(Duration.ZERO);
		assertThat(RetryAfter.parse(" 7 ")).contains(Duration.ofSeconds(7));
		assertThat(RetryAfter.parse("999999999")).contains(Duration.ofSeconds(999_999_999));
	}

	@Test
	void doesNotReadTheRest() {
		assertThat(RetryAfter.parse("-1")).isEmpty();
		assertThat(RetryAfter.parse("1.5")).isEmpty();
		assertThat(RetryAfter.parse("Wed, 21 Oct 2026 07:28:00 GMT")).isEmpty();
		assertThat(RetryAfter.parse("")).isEmpty();
		assertThat(RetryAfter.parse("1000000000")).isEmpty();
		assertThat(RetryAfter.parse(null)).isEmpty();
	}

}
