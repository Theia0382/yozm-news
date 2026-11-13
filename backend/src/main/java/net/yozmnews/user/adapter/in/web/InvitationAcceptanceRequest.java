package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 초대 가입 요청 — 초대 토큰과 가입 칸. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param token 초대 링크의 조각에 실린 토큰
 * @param email 이메일 — 정규화는 서버가 한다
 * @param displayName 표시 이름 — 앞뒤 공백은 서버가 지운다
 * @param password 비밀번호
 * @param keep 로그인 유지 — 가입하면 새 세션으로 로그인한다(user-R8)
 */
record InvitationAcceptanceRequest(@NotNull String token, @NotNull String email, @NotNull String displayName,
		@NotNull String password, @NotNull Boolean keep) {

	@Override
	public String toString() {
		return "InvitationAcceptanceRequest[keep=" + this.keep + "]";
	}

}
