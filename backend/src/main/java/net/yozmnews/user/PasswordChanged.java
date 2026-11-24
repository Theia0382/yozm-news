package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 사용자가 고른 비밀번호로 바꿨다 — 비밀번호 변경 · 복구 재설정이 자격을 바꾼 트랜잭션에서 낸다. 받는 쪽은 커밋 뒤 받아 비밀번호 알림을 남긴다.
 * docs/specs/account.md §3
 *
 * @param user 비밀번호를 바꾼 계정
 * @param method 바꾼 방법
 * @param changedAt 자격을 바꾼 시각 — 같은 변경의 발행이면 언제나 같다
 */
public record PasswordChanged(UserId user, PasswordChangeMethod method, Instant changedAt) {

	public PasswordChanged {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(method, "method");
		Objects.requireNonNull(changedAt, "changedAt");
	}

}
