package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 로그인 요청 — 이메일 · 비밀번호 · 로그인 유지. 칸이 없으면 본문 형식 400 이다(user-R17). docs/specs/user.md §3
 *
 * @param email 이메일 — 정규화는 서버가 한다
 * @param password 비밀번호
 * @param keep 로그인 유지 — 켜면 비활성 7일 · 지속 쿠키(user-R8)
 */
record SignInRequest(@NotNull String email, @NotNull String password, @NotNull Boolean keep) {

	@Override
	public String toString() {
		return "SignInRequest[keep=" + this.keep + "]";
	}

}
