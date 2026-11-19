package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.domain.ClockJumps.Jump;
import org.junit.jupiter.api.Test;

/**
 * 넘긴 기록과 경과 — 넘긴 구간과 겹친 길이를 뺀 경과, 그 경과로 거슬러 가고 나아간 시각. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 넘긴 구간 하나: 11-19 10:00 → 11-20 10:00(24시간). 둘째 구간: 11-20 12:00 → 11-21 12:00.
 */
class ClockJumpsTests {

	private static final Instant D19_0950 = Instant.parse("2026-11-19T09:50:00Z");

	private static final Instant D19_1000 = Instant.parse("2026-11-19T10:00:00Z");

	private static final Instant D20_1000 = Instant.parse("2026-11-20T10:00:00Z");

	private static final Instant D20_1005 = Instant.parse("2026-11-20T10:05:00Z");

	private static final Instant D20_1200 = Instant.parse("2026-11-20T12:00:00Z");

	private static final Instant D21_1200 = Instant.parse("2026-11-21T12:00:00Z");

	private static final ClockJumps ONE = new ClockJumps(List.of(new Jump(D19_1000, D20_1000)));

	private static final ClockJumps TWO = ONE.with(new Jump(D20_1200, D21_1200));

	@Test
	void withoutJumpsTheElapsedTimeIsThePlainDifference() {
		assertThat(ClockJumps.NONE.elapsed(D19_0950, D20_1005)).isEqualTo(Duration.ofMinutes(24 * 60 + 15));
		assertThat(ClockJumps.NONE.before(D20_1005, Duration.ofMinutes(15))).isEqualTo(Instant.parse("2026-11-20T09:50:00Z"));
		assertThat(ClockJumps.NONE.after(D19_0950, Duration.ofMinutes(15))).isEqualTo(Instant.parse("2026-11-19T10:05:00Z"));
		assertThat(ClockJumps.NONE.skipped()).isEqualTo(Duration.ZERO);
	}

	@Test
	void theElapsedTimeLeavesOutTheJumpedSpan() {
		// 09:50 부터 다음 날 10:05 — 넘기기 전 10분 + 넘긴 뒤 5분
		assertThat(ONE.elapsed(D19_0950, D20_1005)).isEqualTo(Duration.ofMinutes(15));
		// 둘 다 넘긴 뒤
		assertThat(ONE.elapsed(D20_1000, D20_1005)).isEqualTo(Duration.ofMinutes(5));
		// 넘긴 구간 안의 시각은 넘긴 뒤 시각과 같은 경과다
		assertThat(ONE.elapsed(Instant.parse("2026-11-19T22:00:00Z"), D20_1005)).isEqualTo(Duration.ofMinutes(5));
		// 두 구간을 지나면 둘 다 뺀다 — 09:50 → 11-21 12:30 은 10분 + 2시간 + 30분
		assertThat(TWO.elapsed(D19_0950, Instant.parse("2026-11-21T12:30:00Z"))).isEqualTo(Duration.ofMinutes(160));
		assertThat(TWO.skipped()).isEqualTo(Duration.ofHours(48));
	}

	@Test
	void aLaterStartGivesNoElapsedTime() {
		assertThat(ONE.elapsed(D20_1005, D19_0950)).isEqualTo(Duration.ZERO);
		assertThat(ONE.elapsed(D20_1005, D20_1005)).isEqualTo(Duration.ZERO);
	}

	@Test
	void beforeWalksBackOverTheJumpedSpan() {
		// 다음 날 10:05 에서 15분 거슬러 가면 넘긴 구간을 건너 09:50
		assertThat(ONE.before(D20_1005, Duration.ofMinutes(15))).isEqualTo(D19_0950);
		// 5분이면 넘긴 구간의 끝 — 그 구간 안의 시각은 경과가 같다
		assertThat(ONE.before(D20_1005, Duration.ofMinutes(5))).isEqualTo(D20_1000);
		// 넘긴 구간에 닿지 않으면 단순한 차
		assertThat(ONE.before(D20_1005, Duration.ofMinutes(3))).isEqualTo(Instant.parse("2026-11-20T10:02:00Z"));
		// 두 구간 — 11-21 12:30 에서 160분 거슬러 가면 09:50
		assertThat(TWO.before(Instant.parse("2026-11-21T12:30:00Z"), Duration.ofMinutes(160))).isEqualTo(D19_0950);
		assertThat(ONE.before(D20_1005, Duration.ZERO)).isEqualTo(D20_1005);
	}

	@Test
	void afterWalksForwardOverTheJumpedSpan() {
		// 09:50 에서 15분 나아가면 넘긴 구간을 건너 다음 날 10:05
		assertThat(ONE.after(D19_0950, Duration.ofMinutes(15))).isEqualTo(D20_1005);
		// 10분이면 넘긴 구간의 시작 — 가장 이른 시각
		assertThat(ONE.after(D19_0950, Duration.ofMinutes(10))).isEqualTo(D19_1000);
		// 넘긴 구간 안에서 출발하면 넘긴 뒤 시각부터 센다
		assertThat(ONE.after(Instant.parse("2026-11-19T22:00:00Z"), Duration.ofMinutes(5))).isEqualTo(D20_1005);
		assertThat(ONE.after(D19_0950, Duration.ZERO)).isEqualTo(D19_0950);
	}

	@Test
	void beforeAndElapsedAgreeOnTheLeaseExpiry() {
		// 09:55 에 점유한 30분 임대 — 다음 날 10:05 의 경과는 5분 + 5분 = 10분이라 지나지 않았다
		Instant claimed = Instant.parse("2026-11-19T09:55:00Z");
		Duration lease = Duration.ofMinutes(30);
		Instant expiredBy = ONE.before(D20_1005, lease).plus(lease);
		assertThat(claimed.plus(lease).isAfter(expiredBy)).isTrue();
		assertThat(ONE.elapsed(claimed, D20_1005)).isEqualTo(Duration.ofMinutes(10));
		// 다음 날 10:30 에는 경과 35분 — 지났다
		Instant later = Instant.parse("2026-11-20T10:30:00Z");
		assertThat(claimed.plus(lease).isAfter(ONE.before(later, lease).plus(lease))).isFalse();
	}

	@Test
	void rejectsOverlappingOrBackwardJumpsAndNegativeElapsedTimes() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Jump(D19_1000, D19_1000));
		assertThatIllegalArgumentException().isThrownBy(() -> ONE.with(new Jump(D19_0950, D19_1000)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ClockJumps(List.of(new Jump(D20_1200, D21_1200), new Jump(D19_1000, D20_1000))));
		assertThatIllegalArgumentException().isThrownBy(() -> ONE.before(D20_1005, Duration.ofMinutes(-1)));
		assertThatIllegalArgumentException().isThrownBy(() -> ONE.after(D19_0950, Duration.ofMinutes(-1)));
	}

}
