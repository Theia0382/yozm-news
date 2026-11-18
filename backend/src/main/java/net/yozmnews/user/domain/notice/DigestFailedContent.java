package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.user.DigestFailureCause;

/**
 * 다이제스트 실패 알림의 내용 — 실패한 다이제스트 · 그 날짜 · 실패의 갈래 · 실패 시각. 모으지 않는다(실패한 다이제스트마다 알림 하나).
 * docs/specs/user.md §4
 *
 * @param digestId 실패한 다이제스트 — 같은 다이제스트의 알림은 하나다. 화면에는 내지 않는다
 * @param digestDate 다이제스트 날짜
 * @param cause 실패의 갈래
 * @param failedAt 실패로 끝낸 시각
 */
public record DigestFailedContent(long digestId, LocalDate digestDate, DigestFailureCause cause, Instant failedAt)
		implements NoticeContent {

	public DigestFailedContent {
		Objects.requireNonNull(digestDate, "digestDate");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(failedAt, "failedAt");
	}

}
