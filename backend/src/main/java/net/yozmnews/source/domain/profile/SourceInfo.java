package net.yozmnews.source.domain.profile;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * 소스(언론사) 정보.
 *
 * @param homepage 홈페이지(절대 http · https URL)
 * @param language 언어 — 날짜 텍스트(오전/오후 · 요일)를 읽는 로캘이기도 하다
 */
public record SourceInfo(String name, URI homepage, Locale language) {

	public SourceInfo {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(homepage, "homepage");
		Objects.requireNonNull(language, "language");
	}

}
