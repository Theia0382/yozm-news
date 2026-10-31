package net.yozmnews.platform.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자 접근 통제 설정 — docs/specs/platform.md §7.
 *
 * @param token 운영자 토큰 — 없거나 비면 운영자 기능이 닫힌다
 */
@ConfigurationProperties("yozmnews.admin")
record AdminSecurityProperties(@Nullable String token) {

	@Override
	public String toString() {
		return "AdminSecurityProperties[token=" + ((this.token == null || this.token.isEmpty()) ? "없음" : "숨김") + "]";
	}

}
