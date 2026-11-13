package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 비밀번호 변경 요청 — 현재 비밀번호 · 새 비밀번호. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param currentPassword 현재 비밀번호 — 강제 변경 중이면 초기 비밀번호
 * @param newPassword 새 비밀번호
 */
record PasswordChangeRequest(@NotNull String currentPassword, @NotNull String newPassword) {

	@Override
	public String toString() {
		return "PasswordChangeRequest[숨김]";
	}

}
