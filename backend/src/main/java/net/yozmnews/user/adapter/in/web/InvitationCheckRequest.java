package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 초대 상태 확인 요청 — 링크의 조각에 실린 토큰. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param token 초대 토큰
 */
record InvitationCheckRequest(@NotNull String token) {

	@Override
	public String toString() {
		return "InvitationCheckRequest[token=숨김]";
	}

}
