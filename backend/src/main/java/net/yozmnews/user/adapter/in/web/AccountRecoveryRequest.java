package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 복구 코드 재설정 요청 — 이메일 · 복구 코드 · 새 비밀번호 · 로그인 유지. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param email 이메일 — 정규화는 서버가 한다
 * @param recoveryCode 복구 코드 — 대소문자 · 공백 · {@code -} 는 서버가 무시한다
 * @param newPassword 새 비밀번호
 * @param keep 로그인 유지 — 재설정하면 새 세션으로 로그인한다(user-R8)
 */
record AccountRecoveryRequest(@NotNull String email, @NotNull String recoveryCode, @NotNull String newPassword,
		@NotNull Boolean keep) {

	@Override
	public String toString() {
		return "AccountRecoveryRequest[keep=" + this.keep + "]";
	}

}
