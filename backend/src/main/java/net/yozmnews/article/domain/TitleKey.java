package net.yozmnews.article.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 제목 비교 키 — docs/specs/article.md §4 · article-R2
 */
public record TitleKey(String value) {

	/** 지우는 공백 문자 */
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

	public TitleKey {
		Objects.requireNonNull(value, "value");
	}

	public static TitleKey of(String title) {
		String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return new TitleKey(WHITESPACE.matcher(normalized).replaceAll(""));
	}

	public boolean isEmpty() {
		return this.value.isEmpty();
	}

	public KeyHash hash() {
		return KeyHash.of(this.value);
	}

}
