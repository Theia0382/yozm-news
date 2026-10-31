package net.yozmnews.source.domain.feed;

import java.util.Locale;

/**
 * 피드 링크 원문의 보정 — docs/specs/source.md §4 · source-R40
 */
final class LinkText {

	/** RFC 3986 이 URI 의 어느 자리에도 허용하지 않는 ASCII 인쇄 문자와 공백 — 퍼센트 인코딩의 대상 */
	private static final String UNSAFE = " \"<>\\^`{|}";

	private LinkText() {
	}

	/**
	 * 대상 문자를 {@code %XX}(대문자 16진)로 바꾼다. 이미 있는 {@code %} 와 그 밖의 문자는 그대로 둔다.
	 * @return 대상 문자가 없으면 입력 그대로
	 */
	static String encodeUnsafe(String text) {
		StringBuilder encoded = null;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (UNSAFE.indexOf(c) < 0) {
				if (encoded != null) {
					encoded.append(c);
				}
				continue;
			}
			if (encoded == null) {
				encoded = new StringBuilder(text.length() + 8).append(text, 0, i);
			}
			encoded.append('%').append(String.format(Locale.ROOT, "%02X", (int) c));
		}
		return (encoded == null) ? text : encoded.toString();
	}

}
