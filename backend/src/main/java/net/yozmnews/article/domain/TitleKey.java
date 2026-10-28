package net.yozmnews.article.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 같은 기사 판정(L2 · guid)용 제목 비교 키 — NFKC 정규화 → 소문자({@link Locale#ROOT}) → 공백 문자 전부 제거. 섹션
 * 피드마다 공백 · 전각/반각 · 대소문자만 다르게 실린 같은 제목을 같은 키로 만든다. 말머리 · 구두점은 지우지 않는다 —
 * {@code [속보] A} 와 뒤이어 나온 본 기사 {@code A} 는 별개 기사다. 키가 비면(공백 문자만의 제목) 제목으로 같은 기사를
 * 판정하지 않는다 — 모든 공백 제목이 같은 키가 되기 때문이다.
 *
 * @param value 키
 */
public record TitleKey(String value) {

	/** 지우는 공백 문자 — ASCII 공백류와 유니코드 구분 문자(Z) */
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
