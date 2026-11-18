package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 관심사 히스토리의 기간 하나 — {@link InterestPeriods} 가 낸다. docs/specs/digest.md §5 · digest-R29
 *
 * @param firstDay 사용자 시간대의 첫날
 * @param lastDay 사용자 시간대의 마지막 날
 * @param start 첫날의 시작 시각
 * @param end 마지막 날 다음 날의 시작 시각
 * @param reference 기준 시각 — 지난 기간은 {@code end}, 진행 중인 기간은 부르는 시각
 * @param inProgress 부르는 시각이 이 기간 안이다
 */
public record InterestPeriod(LocalDate firstDay, LocalDate lastDay, Instant start, Instant end, Instant reference,
		boolean inProgress) {

	public InterestPeriod {
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(reference, "reference");
		if (lastDay.isBefore(firstDay) || !start.isBefore(end)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 앞서야 한다: " + firstDay + " ~ " + lastDay);
		}
		if (reference.isBefore(start) || reference.isAfter(end)) {
			throw new IllegalArgumentException("기준 시각이 기간 밖이다: " + reference);
		}
	}

}
