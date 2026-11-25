package net.yozmnews.report.domain.report;

import java.util.Objects;
import java.util.UUID;

import net.yozmnews.report.ReportId;

/**
 * 생성을 기다리는 리포트와 그 생성 작업의 ID — 예약 · 다시 등록 · 다시 만들기가 커밋 뒤 생성 작업을 등록할 때 넘긴다. docs/specs/report.md §4
 *
 * @param id 리포트
 * @param token 등록 토큰 — 생성 작업의 ID
 */
public record ScheduledReport(ReportId id, UUID token) {

	public ScheduledReport {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(token, "token");
	}

}
