package net.yozmnews.report.domain.view;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import org.jspecify.annotations.Nullable;

/**
 * 조회가 읽은 리포트 행 하나 · docs/specs/report.md §4 · report-R11
 * <p>
 * 요약({@code ReportSummary})은 기간 날짜를 정하는 시간대가 행 밖(시작 전이면 지금 사용자 시간대)일 수 있어 {@link ReportViews#summary} 가
 * 만든다.
 *
 * @param failure 실패 코드 — {@code FAILED} 일 때만
 * @param completedAt 끝낸 시각 — 끝난 상태일 때만
 * @param zone 시작할 때 적은 사용자 시간대 — 시작 전이면 {@code null}
 * @param previous 생성 때 이은 직전 리포트 — 없었거나 그새 지워졌으면 {@code null}
 * @param statsSnapshot 스냅숏(jsonb 를 텍스트로) — 시작 전이면 {@code null}
 * @param regenerations 다시 만든 횟수
 */
public record ReportRow(ReportId id, ReportPeriodKind kind, Instant start, Instant end, ReportStatus status,
		@Nullable ReportFailure failure, Instant createdAt, @Nullable Instant completedAt, @Nullable ZoneId zone,
		@Nullable ReportId previous, @Nullable String provider, @Nullable String model, @Nullable Integer inputTokens,
		@Nullable Integer outputTokens, @Nullable String statsSnapshot, int regenerations) {

	public ReportRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
