package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 첫 로그인 전 계정의 초기 비밀번호 다시 정하기 요청 — 칸이 없으면 본문 형식 400 이다. docs/specs/admin.md §3
 *
 * @param email 계정의 이메일
 * @param initialPassword 새 초기 비밀번호
 */
record InitialPasswordResetRequest(@NotNull String email, @NotNull String initialPassword) {

	@Override
	public String toString() {
		return "InitialPasswordResetRequest[initialPassword=숨김]";
	}

}
