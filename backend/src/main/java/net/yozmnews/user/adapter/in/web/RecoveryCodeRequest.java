package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 복구 코드 재발급 요청 — 현재 비밀번호. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param currentPassword 현재 비밀번호
 */
record RecoveryCodeRequest(@NotNull String currentPassword) {

	@Override
	public String toString() {
		return "RecoveryCodeRequest[숨김]";
	}

}
