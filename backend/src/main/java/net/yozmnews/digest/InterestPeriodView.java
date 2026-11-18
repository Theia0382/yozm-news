package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 관심사 히스토리의 기간 하나 — 사용자 시간대의 날짜와 그 기간 기준 시각의 관심. docs/specs/digest.md §3 · digest-R29
 *
 * @param firstDay 첫날
 * @param lastDay 마지막 날
 * @param inProgress 지금이 이 기간 안이다 — 기준 시각이 지금이다
 * @param point 기준 시각의 관심과 앞 기간 대비 변화
 */
public record InterestPeriodView(LocalDate firstDay, LocalDate lastDay, boolean inProgress, InterestPoint point) {

	public InterestPeriodView {
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		Objects.requireNonNull(point, "point");
	}

}
