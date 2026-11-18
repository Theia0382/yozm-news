package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.user.DigestFailureCause;
import net.yozmnews.user.domain.notice.DigestFailedContent;

/**
 * 다이제스트 실패 알림의 본문 — 다이제스트 날짜 · 실패의 갈래 · 실패 시각. 다이제스트 ID 는 내지 않는다. docs/specs/user.md §3
 *
 * @param digestDate 실패한 다이제스트의 날짜
 * @param cause 실패의 갈래 — 화면이 할 일 문구를 고른다
 * @param failedAt 실패로 끝낸 시각
 */
record DigestFailedNoticeResponse(LocalDate digestDate, DigestFailureCause cause, Instant failedAt) {

	static DigestFailedNoticeResponse of(DigestFailedContent content) {
		return new DigestFailedNoticeResponse(content.digestDate(), content.cause(), content.failedAt());
	}

}
