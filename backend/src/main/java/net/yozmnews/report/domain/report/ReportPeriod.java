package net.yozmnews.report.domain.report;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.report.ReportPeriodKind;

/**
 * 리포트 기간 — 종류와 [시작, 끝) · docs/specs/report.md §5 · report-R1
 */
public record ReportPeriod(ReportPeriodKind kind, Instant start, Instant end) {

	/**
	 * @throws IllegalArgumentException 끝이 시작보다 뒤가 아닐 때
	 */
	public ReportPeriod {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("기간의 끝이 시작보다 뒤가 아니다: " + start + " ~ " + end);
		}
	}

}
