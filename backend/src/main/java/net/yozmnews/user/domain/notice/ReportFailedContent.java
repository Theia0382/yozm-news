package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.user.GenerationFailureCause;
import net.yozmnews.user.ReportFailedKind;

/**
 * 리포트 실패 알림의 내용 — 실패한 리포트 · 종류 · 기간 날짜 · 실패의 갈래 · 실패 시각. 모으지 않는다(실패마다 알림 하나 — 다시 만든 리포트가 또
 * 실패하면 실패 시각이 달라 새 알림이다). docs/specs/user.md §4
 *
 * @param reportId 실패한 리포트 — 화면이 그 리포트로 잇는다
 * @param reportKind 리포트의 종류
 * @param firstDay 기간 첫날
 * @param lastDay 기간 마지막 날
 * @param cause 실패의 갈래
 * @param failedAt 실패로 끝낸 시각
 */
public record ReportFailedContent(long reportId, ReportFailedKind reportKind, LocalDate firstDay, LocalDate lastDay,
		GenerationFailureCause cause, Instant failedAt) implements NoticeContent {

	public ReportFailedContent {
		Objects.requireNonNull(reportKind, "reportKind");
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(failedAt, "failedAt");
	}

}
