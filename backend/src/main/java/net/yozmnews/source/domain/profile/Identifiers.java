package net.yozmnews.source.domain.profile;

import java.util.regex.Pattern;

/**
 * 피드 식별자 · 프리셋 키의 형식 — 소문자 · 숫자를 하이픈 하나로 잇고, 40자 이하다.
 */
final class Identifiers {

	static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	static final int SLUG_MAX_LENGTH = 40;

	private Identifiers() {
	}

	static boolean isSlug(String value) {
		return value.length() <= SLUG_MAX_LENGTH && SLUG.matcher(value).matches();
	}

	static String requireSlug(String value, String what) {
		if (!isSlug(value)) {
			throw new IllegalArgumentException(what + " 형식이 아니다(소문자 · 숫자 · 하이픈, " + SLUG_MAX_LENGTH + "자 이하): " + value);
		}
		return value;
	}

}
