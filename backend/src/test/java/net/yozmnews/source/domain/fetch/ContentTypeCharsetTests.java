package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Content-Type 의 charset 매개변수.
 */
class ContentTypeCharsetTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "text/xml; charset=EUC-KR|EUC-KR", "text/xml; charset=\"utf-8\"|utf-8",
			"text/xml; CHARSET=x|x", "application/rss+xml; q=1; charset = windows-949 ; v=2|windows-949",
			"text/xml;charset=\" utf-8 \"|utf-8" })
	void readsTheCharset(String contentType, String expected) {
		assertThat(ContentTypeCharset.of(contentType)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "text/xml", "text/xml; charset=", "text/xml; charset=\"\"", "text/xml; format=flowed",
			"charset=utf-8" })
	void nullWhenThereIsNoCharset(@Nullable String contentType) {
		assertThat(ContentTypeCharset.of(contentType)).isNull();
	}

}
