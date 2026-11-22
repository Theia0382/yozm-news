package net.yozmnews.report.domain.view;

import java.util.Objects;

import net.yozmnews.report.ReportSummary;
import org.jspecify.annotations.Nullable;

/**
 * 조회가 읽은 리포트 행 하나 · docs/specs/report.md §4 · report-R11
 *
 * @param statsSnapshot 스냅숏(jsonb 를 텍스트로) — 시작 전이면 {@code null}
 */
public record ReportRow(ReportSummary summary, @Nullable String provider, @Nullable String model,
		@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable String statsSnapshot) {

	public ReportRow {
		Objects.requireNonNull(summary, "summary");
	}

}
