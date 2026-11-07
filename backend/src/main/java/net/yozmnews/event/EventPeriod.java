package net.yozmnews.event;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.DayRange;

/**
 * 사건 조회의 기간 — 시작 포함 · 끝 제외 {@code [from, to)}, 시간대 없음 · docs/specs/event.md §3 · event-R20.
 * <p>
 * 기간 안 기사는 배정의 발행 시각으로 고른다.
 */
public record EventPeriod(Instant from, Instant to) {

	/** 날짜 범위의 상한(일) — 날짜 수로 잰다. 정본. 측정 전 목표 */
	public static final int MAX_DAYS = 31;

	/** 시각 범위의 상한 — 날짜 {@link #MAX_DAYS} 일 범위의 시각 길이 상한({@link DayRange#maxLength}) */
	public static final Duration MAX_LENGTH = DayRange.maxLength(MAX_DAYS);

	/**
	 * @throws IllegalArgumentException {@code from < to} 가 아니거나 길이가 {@link #MAX_LENGTH} 를 넘을 때
	 */
	public EventPeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!valid(from, to)) {
			throw new IllegalArgumentException("기간이 범위 밖이다: " + from + " ~ " + to);
		}
	}

	/**
	 * 범위를 판정해 만든다.
	 * @return {@code from < to} 가 아니거나 길이가 {@link #MAX_LENGTH} 를 넘으면 빈 값
	 */
	public static Optional<EventPeriod> of(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		return valid(from, to) ? Optional.of(new EventPeriod(from, to)) : Optional.empty();
	}

	/**
	 * 날짜 범위 — 그 시간대의 자정으로 옮긴다({@link DayRange#of}). 시간대는 결과에 남지 않는다.
	 * @return 날짜 수가 1 ~ {@link #MAX_DAYS} 가 아니거나 시각 범위를 만들 수 없으면 빈 값
	 */
	public static Optional<EventPeriod> ofDays(LocalDate first, LocalDate last, ZoneId zone) {
		return DayRange.of(first, last, zone, MAX_DAYS).flatMap(range -> of(range.from(), range.to()));
	}

	private static boolean valid(Instant from, Instant to) {
		return from.isBefore(to) && Duration.between(from, to).compareTo(MAX_LENGTH) <= 0;
	}

}
