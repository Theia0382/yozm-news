package net.yozmnews.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.DayRange;

/**
 * 판정 원본 조회 기간 — 시간대 없는 {@code [from, to)} 시각 범위 · docs/specs/evaluation.md §3 · evaluation-R39.
 * <p>
 * 범위 밖 기간은 만들어지지 않는다 — {@code from < to} 이고 길이가 {@link #MAX_LENGTH} 이하다. 날짜 범위는 {@link #ofDays} 가 부르는
 * 쪽의 시간대 자정으로 옮긴다.
 */
public record JudgementPeriod(Instant from, Instant to) {

	/** 날짜 범위의 상한(일) — 날짜 수로 잰다 */
	public static final int MAX_DAYS = 31;

	/** 시각 범위의 상한 — 날짜 {@link #MAX_DAYS} 일 범위의 시각 길이 상한({@link DayRange#maxLength}) */
	public static final Duration MAX_LENGTH = DayRange.maxLength(MAX_DAYS);

	/**
	 * @throws IllegalArgumentException {@code from < to} 가 아니거나 길이가 {@link #MAX_LENGTH} 를 넘을 때
	 */
	public JudgementPeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!valid(from, to)) {
			throw new IllegalArgumentException(
					"판정 원본 조회 기간은 시작이 끝보다 앞서고 길이가 " + MAX_LENGTH.toDays() + "일 이하다: " + from + " ~ " + to);
		}
	}

	/** 시각 범위 — 범위 밖이면 빈 값 */
	public static Optional<JudgementPeriod> of(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		return valid(from, to) ? Optional.of(new JudgementPeriod(from, to)) : Optional.empty();
	}

	/**
	 * 날짜 범위 — 그 시간대의 자정으로 옮긴다({@link DayRange#of}). 시간대는 결과에 남지 않는다.
	 * @return 날짜 수가 1 ~ {@link #MAX_DAYS} 가 아니거나 시각 범위를 만들 수 없으면 빈 값
	 */
	public static Optional<JudgementPeriod> ofDays(LocalDate first, LocalDate last, ZoneId zone) {
		return DayRange.of(first, last, zone, MAX_DAYS).flatMap(range -> of(range.from(), range.to()));
	}

	private static boolean valid(Instant from, Instant to) {
		return from.isBefore(to) && Duration.between(from, to).compareTo(MAX_LENGTH) <= 0;
	}

}
