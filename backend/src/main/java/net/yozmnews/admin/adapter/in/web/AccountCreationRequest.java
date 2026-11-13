package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 운영자 계정 생성 요청 — 칸이 없으면 본문 형식 400 이다. docs/specs/admin.md §3
 *
 * @param email 이메일 — 정규화는 서버가 한다
 * @param displayName 표시 이름
 * @param initialPassword 초기 비밀번호 — 계정 주인이 첫 로그인에서 바꾼다
 */
record AccountCreationRequest(@NotNull String email, @NotNull String displayName, @NotNull String initialPassword) {

	@Override
	public String toString() {
		return "AccountCreationRequest[initialPassword=숨김]";
	}

}
