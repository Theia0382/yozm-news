package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.domain.notice.PasswordChangeMethod;
import net.yozmnews.user.domain.notice.PasswordChangedContent;

/**
 * 비밀번호 알림의 본문 — 바꾼 방법과 시각. docs/specs/user.md §3
 *
 * @param method 바꾼 방법 — 비밀번호 변경 · 복구 코드 재설정
 * @param changedAt 바꾼 시각
 */
record PasswordChangedNoticeResponse(PasswordChangeMethod method, Instant changedAt) {

	static PasswordChangedNoticeResponse of(PasswordChangedContent content) {
		return new PasswordChangedNoticeResponse(content.method(), content.changedAt());
	}

}
