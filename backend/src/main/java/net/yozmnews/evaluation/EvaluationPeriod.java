package net.yozmnews.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * 집계 기간 — 시간대 없는 {@code [from, to)} 시각 범위 · docs/specs/evaluation.md §3.
 * <p>
 * 범위 밖 기간은 만들어지지 않는다 — {@code from < to} 이고 길이가 {@link #MAX_LENGTH} 이하다. 날짜 범위는 {@link #ofDays} 가 부르는
 * 쪽의 시간대 자정으로 옮긴다.
 */
public record EvaluationPeriod(Instant from, Instant to) {

	/** 날짜 범위의 상한(일) — 날짜 수로 잰다 */
	public static final int MAX_DAYS = 31;

	/** 시각 범위의 상한 — 날짜 {@link #MAX_DAYS} 일에 시간대 전환 여유로 하루를 더한다 */
	public static final Duration MAX_LENGTH = Duration.ofDays(MAX_DAYS + 1);

	/**
	 * @throws IllegalArgumentException {@code from < to} 가 아니거나 길이가 {@link #MAX_LENGTH} 를 넘을 때
	 */
	public EvaluationPeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!valid(from, to)) {
			throw new IllegalArgumentException("집계 기간은 시작이 끝보다 앞서고 길이가 " + MAX_LENGTH.toDays() + "일 이하다: " + from + " ~ " + to);
		}
	}

	/** 시각 범위 — 범위 밖이면 빈 값 */
	public static Optional<EvaluationPeriod> of(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		return valid(from, to) ? Optional.of(new EvaluationPeriod(from, to)) : Optional.empty();
	}

	/**
	 * 날짜 범위 — {@code [first 의 그 시간대 자정, last 다음 날의 그 시간대 자정)}. 자정이 없는 날은 그날의 첫 유효 시각이다
	 * ({@link LocalDate#atStartOfDay(ZoneId)}). 시간대는 결과에 남지 않는다.
	 * @return 날짜 수({@code last − first + 1})가 1 ~ {@link #MAX_DAYS} 가 아니거나, 만든 시각 범위가 {@link #of} 의 규칙을 어기면(하루가
	 *         통째로 없는 날) 빈 값
	 */
	public static Optional<EvaluationPeriod> ofDays(LocalDate first, LocalDate last, ZoneId zone) {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(last, "last");
		Objects.requireNonNull(zone, "zone");
		long days = ChronoUnit.DAYS.between(first, last) + 1;
		// 마지막 날짜의 다음 날이 없으면 끝을 만들 수 없다
		if (days < 1 || days > MAX_DAYS || last.equals(LocalDate.MAX)) {
			return Optional.empty();
		}
		return of(first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant());
	}

	private static boolean valid(Instant from, Instant to) {
		return from.isBefore(to) && Duration.between(from, to).compareTo(MAX_LENGTH) <= 0;
	}

}
