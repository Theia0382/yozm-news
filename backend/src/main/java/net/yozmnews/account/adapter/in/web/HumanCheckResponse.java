package net.yozmnews.account.adapter.in.web;

import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import org.jspecify.annotations.Nullable;

/**
 * 봇 차단 설정 — 화면이 로그인 · 복구 · 공개 가입 화면에 위젯을 띄울지 정한다. docs/specs/account.md §3 · account-R33
 *
 * @param provider 끔 · Turnstile
 * @param siteKey Turnstile 이면 위젯의 사이트 키(공개 값), 끔이면 null
 */
record HumanCheckResponse(HumanCheckProvider provider, @Nullable String siteKey) {

	static HumanCheckResponse of(HumanCheckSetting setting) {
		return switch (setting) {
			case HumanCheckSetting.Off off -> new HumanCheckResponse(HumanCheckProvider.OFF, null);
			case HumanCheckSetting.Turnstile turnstile ->
				new HumanCheckResponse(HumanCheckProvider.TURNSTILE, turnstile.siteKey());
		};
	}

	/** 봇 차단 공급자의 판별자 */
	enum HumanCheckProvider {

		/** 끔 — 위젯이 없고 토큰 없이 보낸다 */
		OFF,

		/** Cloudflare Turnstile */
		TURNSTILE

	}

}
