package net.yozmnews.user.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 첫 운영자 설정 {@code yozmnews.first-admin} — 운영자가 없을 때 이 이메일 · 초기 비밀번호로 운영자 계정을 만든다. docs/specs/user.md §7
 *
 * @param email 이메일 — 없거나 비면 없음
 * @param password 초기 비밀번호 — 없거나 비면 없음. 첫 로그인에서 바꿔야 한다
 */
@ConfigurationProperties("yozmnews.first-admin")
record FirstAdminProperties(@Nullable String email, @Nullable String password) {

	@Override
	public String toString() {
		return "FirstAdminProperties[email=" + ((this.email == null || this.email.isBlank()) ? "없음" : "있음")
				+ ", password=" + ((this.password == null || this.password.isEmpty()) ? "없음" : "숨김") + "]";
	}

}
