package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

import net.yozmnews.TestClocks;
import net.yozmnews.TestClocks.MovableClock;
import net.yozmnews.platform.application.SystemClock;
import org.junit.jupiter.api.Test;

/**
 * 앱 시계의 경과 판정 기본 메서드 — 지금까지의 경과, 지금에서 거슬러 간 시각, 임대 만료 시각의 상한. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 11-19 09:55 에 점유한 30분 임대를 본다.
 */
class AppClockTests {

	private static final Instant CLAIMED = Instant.parse("2026-11-19T09:55:00Z");

	private static final Duration LEASE = Duration.ofMinutes(30);

	@Test
	void withoutJumpsTheLeaseExpiryLimitIsNow() {
		AppClock clock = new SystemClock(InstantSource.fixed(Instant.parse("2026-11-19T10:05:00Z")));

		assertThat(clock.realTime()).isTrue();
		assertThat(clock.elapsedSince(CLAIMED)).isEqualTo(Duration.ofMinutes(10));
		assertThat(clock.before(LEASE)).isEqualTo(Instant.parse("2026-11-19T09:35:00Z"));
		assertThat(clock.expiredBy(LEASE)).isEqualTo(Instant.parse("2026-11-19T10:05:00Z"));
	}

	@Test
	void aJumpDoesNotCountTowardsTheLease() {
		// 5분 흐르고 24시간 넘긴 뒤 5분 흐른다 — 지금 11-20 10:05, 점유부터의 경과 10분
		MovableClock clock = TestClocks.movable(CLAIMED)
			.advance(Duration.ofMinutes(5))
			.jump(Duration.ofHours(24))
			.advance(Duration.ofMinutes(5));

		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-20T10:05:00Z"));
		assertThat(clock.elapsedSince(CLAIMED)).isEqualTo(Duration.ofMinutes(10));
		assertThat(clock.before(LEASE)).isEqualTo(Instant.parse("2026-11-19T09:35:00Z"));
		// 만료 시각 11-19 10:25 가 상한 11-19 10:05 보다 뒤라 지나지 않았다
		assertThat(clock.expiredBy(LEASE)).isEqualTo(Instant.parse("2026-11-19T10:05:00Z"));

		// 25분 더 흘러 11-20 10:30 — 경과 35분, 상한이 지금이 되어 지났다
		clock.advance(Duration.ofMinutes(25));
		assertThat(clock.elapsedSince(CLAIMED)).isEqualTo(Duration.ofMinutes(35));
		assertThat(clock.expiredBy(LEASE)).isEqualTo(Instant.parse("2026-11-20T10:30:00Z"));
	}

}
