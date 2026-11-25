package net.yozmnews.report.domain.input;

import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.event.EventId;

/**
 * 사건 줄 — 사용자 스토리라인의 기간 안 기사가 있는 사건 하나 · docs/specs/report.md §5 · report-R16
 *
 * @param title 대표 기사 제목
 * @param firstDay 첫 기사 시각의 사용자 시간대 날짜
 * @param articlesInPeriod 기간 안 기사 수 — 1 이상
 * @param startedInPeriod 첫 기사 시각이 기간 안인가
 */
public record EventLine(EventId event, String title, LocalDate firstDay, int articlesInPeriod,
		boolean startedInPeriod) {

	/**
	 * @throws IllegalArgumentException 기간 안 기사 수가 1 보다 작을 때
	 */
	public EventLine {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(firstDay, "firstDay");
		if (articlesInPeriod < 1) {
			throw new IllegalArgumentException("사건 줄은 기간 안 기사가 있는 사건이다: " + articlesInPeriod);
		}
	}

}
