package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.user.GenerationFailureCause;
import net.yozmnews.user.ReportFailedKind;
import net.yozmnews.user.domain.notice.ReportFailedContent;

/**
 * 리포트 실패 알림의 본문 — 실패한 리포트 · 종류 · 기간 날짜 · 실패의 갈래 · 실패 시각. docs/specs/user.md §3
 *
 * @param reportId 실패한 리포트 — 화면이 그 리포트로 잇는다
 * @param kind 종류
 * @param firstDay 기간 첫날
 * @param lastDay 기간 마지막 날
 * @param cause 실패의 갈래 — 화면이 할 일 문구를 고른다
 * @param failedAt 실패로 끝낸 시각
 */
record ReportFailedNoticeResponse(long reportId, ReportFailedKind kind, LocalDate firstDay, LocalDate lastDay,
		GenerationFailureCause cause, Instant failedAt) {

	static ReportFailedNoticeResponse of(ReportFailedContent content) {
		return new ReportFailedNoticeResponse(content.reportId(), content.reportKind(), content.firstDay(), content.lastDay(),
				content.cause(), content.failedAt());
	}

}
