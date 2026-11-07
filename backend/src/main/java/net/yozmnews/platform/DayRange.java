package net.yozmnews.platform;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * 날짜 범위를 시각 범위로 옮긴 값 — 여러 모듈의 기간 타입이 함께 쓰는 변환의 정본 · docs/specs/platform.md §3.
 * <p>
 * 날짜 수의 상한은 부르는 기간 타입이 갖는다. 시간대는 결과에 남지 않는다.
 */
public record DayRange(Instant from, Instant to) {

	/**
	 * @throws IllegalArgumentException {@code from < to} 가 아닐 때
	 */
	public DayRange {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("시작이 끝보다 앞서야 한다: " + from + " ~ " + to);
		}
	}

	/**
	 * 날짜 범위 — {@code [first 의 그 시간대 자정, last 다음 날의 그 시간대 자정)}. 자정이 없는 날은 그날의 첫 유효 시각이다
	 * ({@link LocalDate#atStartOfDay(ZoneId)}).
	 * @return 날짜 수({@code last − first + 1})가 1 ~ {@code maxDays} 가 아니거나, {@code last} 가 {@link LocalDate#MAX} 이거나,
	 *         만든 시각 범위가 {@code from < to} 가 아니면(하루가 통째로 없는 날) 빈 값
	 */
	public static Optional<DayRange> of(LocalDate first, LocalDate last, ZoneId zone, int maxDays) {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(last, "last");
		Objects.requireNonNull(zone, "zone");
		long days = ChronoUnit.DAYS.between(first, last) + 1;
		// 마지막 날짜의 다음 날이 없으면 끝을 만들 수 없다
		if (days < 1 || days > maxDays || last.equals(LocalDate.MAX)) {
			return Optional.empty();
		}
		Instant from = first.atStartOfDay(zone).toInstant();
		Instant to = last.plusDays(1).atStartOfDay(zone).toInstant();
		return from.isBefore(to) ? Optional.of(new DayRange(from, to)) : Optional.empty();
	}

	/**
	 * 날짜 {@code maxDays} 일 범위가 가질 수 있는 시각 길이의 상한 — 정본. 시간대 전환 여유로 하루를 더한다.
	 * @throws IllegalArgumentException {@code maxDays} 가 1 보다 작을 때
	 */
	public static Duration maxLength(int maxDays) {
		if (maxDays < 1) {
			throw new IllegalArgumentException("날짜 수 상한은 1 이상이다: " + maxDays);
		}
		return Duration.ofDays(maxDays + 1L);
	}

}
