package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.Objects;

/**
 * 비밀번호 알림의 내용 — 바꾼 방법과 시각. 모으지 않는다(바뀔 때마다 알림 하나). 첫 로그인의 강제 변경은 알리지 않는다. docs/specs/user.md §4
 *
 * @param method 바꾼 방법
 * @param changedAt 바꾼 시각
 */
public record PasswordChangedContent(PasswordChangeMethod method, Instant changedAt) implements NoticeContent {

	public PasswordChangedContent {
		Objects.requireNonNull(method, "method");
		Objects.requireNonNull(changedAt, "changedAt");
	}

}
