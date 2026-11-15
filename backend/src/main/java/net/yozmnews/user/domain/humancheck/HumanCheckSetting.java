package net.yozmnews.user.domain.humancheck;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 봇 차단 설정 — 끔 · Turnstile(사이트 키). 설정 값의 판정 정본이다. docs/specs/user.md §7 · user-R41
 * <p>
 * 비밀 키는 여기서 보지 않는다 — 비밀 키 유무는 검증기의 등록 조건이다.
 */
public sealed interface HumanCheckSetting {

	/** 공급자 설정 이름 */
	String PROVIDER_PROPERTY = "yozmnews.human-check.provider";

	/** 사이트 키 설정 이름 */
	String SITE_KEY_PROPERTY = "yozmnews.human-check.turnstile.site-key";

	/** 사이트 키 환경 변수 이름 — 운영자 문구에 쓴다 */
	String SITE_KEY_ENVIRONMENT_VARIABLE = "YOZMNEWS_HUMAN_CHECK_TURNSTILE_SITE_KEY";

	/** 비밀 키 설정 이름 — 비밀 키 유무는 검증기의 등록 조건이 읽는다 */
	String SECRET_KEY_PROPERTY = "yozmnews.human-check.turnstile.secret-key";

	/** 비밀 키 환경 변수 이름 — 운영자 문구에 쓴다 */
	String SECRET_KEY_ENVIRONMENT_VARIABLE = "YOZMNEWS_HUMAN_CHECK_TURNSTILE_SECRET_KEY";

	/** 봇 차단을 끈다 — 요청 제한만 남는다 */
	record Off() implements HumanCheckSetting {

	}

	/**
	 * Turnstile 로 확인한다.
	 * @param siteKey 화면 위젯의 사이트 키 — 공개 값
	 */
	record Turnstile(String siteKey) implements HumanCheckSetting {

		public Turnstile {
			Objects.requireNonNull(siteKey, "siteKey");
			if (siteKey.isBlank()) {
				throw new IllegalArgumentException("사이트 키가 비었다");
			}
		}

	}

	/**
	 * 설정 값을 판정한다 — 없음 · 빈 값 · {@code off} 는 끔, {@code turnstile} 은 사이트 키가 있어야 한다. 대소문자를 가리지 않는다.
	 * @throws IllegalArgumentException 모르는 공급자이거나 Turnstile 에 사이트 키가 없을 때 — 문구에 값을 싣지 않는다
	 */
	static HumanCheckSetting of(@Nullable String provider, @Nullable String siteKey) {
		String name = (provider == null) ? "" : provider.strip().toLowerCase(Locale.ROOT);
		return switch (name) {
			case "", "off" -> new Off();
			case "turnstile" -> {
				if (siteKey == null || siteKey.isBlank()) {
					throw new IllegalArgumentException("봇 차단을 Turnstile 로 켜려면 사이트 키(" + SITE_KEY_PROPERTY + ", 환경 변수 "
							+ SITE_KEY_ENVIRONMENT_VARIABLE + ")가 있어야 한다");
				}
				yield new Turnstile(siteKey.strip());
			}
			default -> throw new IllegalArgumentException(
					PROVIDER_PROPERTY + "(환경 변수 YOZMNEWS_HUMAN_CHECK_PROVIDER)는 off 또는 turnstile 이다");
		};
	}

}
