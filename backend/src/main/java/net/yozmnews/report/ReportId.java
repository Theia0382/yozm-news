package net.yozmnews.report;

/**
 * 리포트 식별자 · docs/specs/report.md §3
 */
public record ReportId(long value) {

	public ReportId {
		if (value < 1) {
			throw new IllegalArgumentException("리포트 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
