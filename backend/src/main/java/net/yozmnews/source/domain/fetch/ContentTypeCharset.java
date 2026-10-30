package net.yozmnews.source.domain.fetch;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * HTTP {@code Content-Type} 의 charset 매개변수 읽기 — docs/specs/source.md §4 · source-R38
 */
public final class ContentTypeCharset {

	private ContentTypeCharset() {
	}

	/**
	 * charset 매개변수 값을 읽는다 — 매개변수 이름은 대소문자를 무시하고, 값의 앞뒤 공백과 감싼 따옴표를 지운다. 레이블이 지원되는지는 보지
	 * 않는다 — 정규화가 판정한다.
	 * @return 없거나 값이 비었으면 null
	 */
	public static @Nullable String of(@Nullable String contentType) {
		if (contentType == null) {
			return null;
		}
		String[] parts = contentType.split(";");
		for (int i = 1; i < parts.length; i++) {
			String parameter = parts[i];
			int equals = parameter.indexOf('=');
			if (equals < 0 || !parameter.substring(0, equals).strip().toLowerCase(Locale.ROOT).equals("charset")) {
				continue;
			}
			String value = unquote(parameter.substring(equals + 1).strip()).strip();
			return value.isEmpty() ? null : value;
		}
		return null;
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

}
