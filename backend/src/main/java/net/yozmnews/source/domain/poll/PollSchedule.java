package net.yozmnews.source.domain.poll;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.domain.fetch.FailurePermanence;
import net.yozmnews.source.domain.fetch.FetchLimits;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;

/**
 * 수집 일정의 산식과 상수 — 정본 · docs/specs/source.md §4 · source-R15 · source-R18 · source-R19 · source-R25.
 * <p>
 * 값은 측정 전 목표이고 운영 데이터로 다시 판단한다.
 */
public final class PollSchedule {

	/** 일시 실패의 백오프 상한 */
	public static final Duration TEMPORARY_BACKOFF_CAP = Duration.ofHours(6);

	/** 영구 실패의 백오프 상한 */
	public static final Duration PERMANENT_BACKOFF_CAP = Duration.ofHours(24);

	/**
	 * 점유가 풀리지 않아도 다시 대상이 되기까지 — 예약 시각부터 잰다. 작업 서버의 poll 간격, 수집 마감, 마감 밖의 이름 해석을 합친
	 * 것보다 넉넉해야 한다.
	 */
	public static final Duration LEASE = Duration.ofMinutes(10);

	/** 프로필이나 그 안의 피드를 얻지 못한 피드를 다시 확인하기까지 */
	public static final Duration PROFILE_RECHECK = Duration.ofHours(1);

	/** 같은 소스의 요청 시작 사이 간격 — 수집 마감과 같다 */
	public static final Duration SOURCE_SPACING = FetchLimits.DEADLINE;

	private PollSchedule() {
	}

	/** 성공 · 변경 없음 뒤의 다음 수집 시각을 정한다 — source-R25 */
	public static Instant afterSuccess(Instant completedAt, PollInterval interval) {
		return completedAt.plus(interval.value());
	}

	/**
	 * 실패한 뒤의 다음 수집 시각을 정한다 — source-R25. 곱은 상한에 닿으면 더 키우지 않으므로 연속 실패가 많아도 넘치지 않는다.
	 * @param consecutiveFailures 이번 실패를 포함한 연속 실패 수
	 * @throws IllegalArgumentException 연속 실패 수가 1 보다 작을 때
	 */
	public static Instant afterFailure(Instant attemptedAt, PollInterval interval, int consecutiveFailures,
			FailurePermanence permanence) {
		Objects.requireNonNull(attemptedAt, "attemptedAt");
		Objects.requireNonNull(permanence, "permanence");
		if (consecutiveFailures < 1) {
			throw new IllegalArgumentException("연속 실패 수는 1 이상이다: " + consecutiveFailures);
		}
		Duration cap = switch (permanence) {
			case TEMPORARY -> TEMPORARY_BACKOFF_CAP;
			case PERMANENT -> PERMANENT_BACKOFF_CAP;
		};
		Duration delay = interval.value();
		for (int i = 0; i < consecutiveFailures && delay.compareTo(cap) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		if (delay.compareTo(cap) > 0) {
			delay = cap;
		}
		if (delay.compareTo(interval.value()) < 0) {
			delay = interval.value();
		}
		return attemptedAt.plus(delay);
	}

	/**
	 * 점유의 예약 시각까지 남은 시간을 구한다 — 지났으면 0. JobRunr 작업 서버는 poll 간격 안에 든 예약 작업을 일찍 시작하므로, 수집
	 * 작업은 예약 시각까지 기다린 뒤 요청 시작 시각을 예약한다.
	 */
	public static Duration waitBeforeFetch(Lease lease, Instant now) {
		return remaining(lease.scheduledAt(), now);
	}

	/**
	 * 같은 소스의 요청 시작 시각을 예약한다 — source-R18.
	 * @param nextFetchAt 그 소스의 다음 요청 가능 시각 — 아직 요청한 적이 없으면 null
	 */
	public static FetchSlot reserveFetch(@Nullable Instant nextFetchAt, Instant now) {
		Objects.requireNonNull(now, "now");
		Instant start = (nextFetchAt != null && nextFetchAt.isAfter(now)) ? nextFetchAt : now;
		return new FetchSlot(start, start.plus(SOURCE_SPACING));
	}

	/**
	 * 예약한 시작 시각에 요청해도 점유가 유효한 동안 끝나는지 본다 — source-R18.
	 * @param leaseExpiresAt 점유가 만료되는 시각 — 점유 중인 상태의 다음 수집 시각
	 */
	public static boolean fitsLease(FetchSlot slot, Instant leaseExpiresAt) {
		return slot.start().plus(FetchLimits.DEADLINE).isBefore(leaseExpiresAt);
	}

	/** 시각까지 남은 시간을 구한다 — 지났으면 0 */
	public static Duration remaining(Instant target, Instant now) {
		Duration remaining = Duration.between(now, target);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	/**
	 * 예약한 요청 시작
	 */
	public record FetchSlot(Instant start, Instant next) {

		public FetchSlot {
			Objects.requireNonNull(start, "start");
			Objects.requireNonNull(next, "next");
		}

	}

}
