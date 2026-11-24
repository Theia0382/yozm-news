package net.yozmnews.account.adapter.out.turnstile;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 봇 차단 설정 {@code yozmnews.human-check} — docs/specs/user.md §7 · user-R41
 *
 * @param provider 공급자 — {@code off}(기본) · {@code turnstile}
 * @param turnstile Turnstile 의 사이트 키 · 비밀 키
 */
@ConfigurationProperties("yozmnews.human-check")
record HumanCheckProperties(@Nullable String provider, @Nullable Turnstile turnstile) {

	/** 사이트 키 — 없으면 null */
	@Nullable String siteKey() {
		return (this.turnstile == null) ? null : this.turnstile.siteKey();
	}

	/** 비밀 키 — 없으면 null */
	@Nullable String secretKey() {
		return (this.turnstile == null) ? null : this.turnstile.secretKey();
	}

	/**
	 * @param siteKey 화면 위젯의 사이트 키 — 공개 값
	 * @param secretKey 서버 검증의 비밀 키 — 비밀 값
	 */
	record Turnstile(@Nullable String siteKey, @Nullable String secretKey) {

		@Override
		public String toString() {
			return "Turnstile[siteKey=" + this.siteKey + ", secretKey="
					+ ((this.secretKey == null || this.secretKey.isEmpty()) ? "없음" : "숨김") + "]";
		}

	}

}
