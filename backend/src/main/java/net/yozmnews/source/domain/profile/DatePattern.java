package net.yozmnews.source.domain.profile;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 발행 시각을 읽는 날짜 형식 하나.
 *
 * @param pattern 프로필에 적힌 패턴
 * @param formatter 소스 언어의 로캘로 컴파일한 형식
 */
public record DatePattern(String pattern, DateTimeFormatter formatter) {

	public DatePattern {
		Objects.requireNonNull(pattern, "pattern");
		Objects.requireNonNull(formatter, "formatter");
	}

}
