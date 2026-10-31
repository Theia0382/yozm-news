package net.yozmnews.source.domain.profile;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 발행 시각을 읽는 날짜 형식 하나.
 * <p>
 * 같음은 패턴 문자열로 본다 — 형식기는 패턴과 소스 언어에서만 만들어지고({@code ProfileValidator}), {@link DateTimeFormatter} 는 값
 * 비교를 하지 않는다. 언어는 피드 읽기 설정({@link FeedReading})이 따로 비교한다.
 *
 * @param pattern 프로필에 적힌 패턴
 * @param formatter 소스 언어의 로캘로 컴파일한 형식
 */
public record DatePattern(String pattern, DateTimeFormatter formatter) {

	public DatePattern {
		Objects.requireNonNull(pattern, "pattern");
		Objects.requireNonNull(formatter, "formatter");
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof DatePattern that) && this.pattern.equals(that.pattern);
	}

	@Override
	public int hashCode() {
		return this.pattern.hashCode();
	}

}
