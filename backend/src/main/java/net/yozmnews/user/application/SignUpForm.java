package net.yozmnews.user.application;

import java.util.Objects;

/**
 * 가입 칸 — 이메일 · 표시 이름 · 비밀번호. 형식은 가입 유스케이스가 판정한다. docs/specs/user.md §4
 *
 * @param email 이메일 — 정규화 전
 * @param displayName 표시 이름 — 앞뒤 공백을 지우기 전
 * @param password 비밀번호 — 정규화 전
 */
public record SignUpForm(String email, String displayName, String password) {

	public SignUpForm {
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(password, "password");
	}

	@Override
	public String toString() {
		return "SignUpForm[password=숨김]";
	}

}
