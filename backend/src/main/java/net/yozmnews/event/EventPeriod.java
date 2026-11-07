package net.yozmnews.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 사건 조회의 기간 — 시작 포함 · 끝 제외 {@code [from, to)}, 시간대 없음 · docs/specs/event.md §3 · event-R20.
 * <p>
 * 기간 안 기사는 배정의 발행 시각으로 고른다.
 */
public record EventPeriod(Instant from, Instant to) {

	/** 기간 길이의 상한(일) — 정본. 측정 전 목표 */
	public static final int MAX_DAYS = 31;

	/** 기간 길이의 상한 */
	public static final Duration MAX_LENGTH = Duration.ofDays(MAX_DAYS);

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

	private static boolean valid(Instant from, Instant to) {
		return from.isBefore(to) && Duration.between(from, to).compareTo(MAX_LENGTH) <= 0;
	}

}
