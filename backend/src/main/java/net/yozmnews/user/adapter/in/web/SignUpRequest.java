package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 공개 가입 요청 — 이메일 · 표시 이름 · 비밀번호 · 로그인 유지. 칸이 없으면 본문 형식 400 이다. docs/specs/user.md §3
 *
 * @param email 이메일 — 정규화는 서버가 한다
 * @param displayName 표시 이름 — 앞뒤 공백은 서버가 지운다
 * @param password 비밀번호
 * @param keep 로그인 유지 — 가입하면 새 세션으로 로그인한다(user-R8)
 */
record SignUpRequest(@NotNull String email, @NotNull String displayName, @NotNull String password,
		@NotNull Boolean keep) {

	@Override
	public String toString() {
		return "SignUpRequest[keep=" + this.keep + "]";
	}

}
