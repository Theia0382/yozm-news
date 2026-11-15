package net.yozmnews.user.domain.interest;

import java.util.Objects;
import java.util.Optional;

/**
 * 자유 텍스트 관심사 — 앞뒤 공백을 지운 0 ~ 500 코드 포인트. 빈 문자열은 관심사가 없다는 뜻이다 — docs/specs/user.md §5 · user-R50
 */
public record InterestText(String value) {

	/** 코드 포인트 상한 — 규칙의 정본 */
	public static final int MAX_CODE_POINTS = 500;

	/** 관심사 없음 */
	public static final InterestText NONE = new InterestText("");

	/**
	 * @throws IllegalArgumentException 규칙을 어길 때 — {@link #parse} 로 거른 값만 만든다
	 */
	public InterestText {
		Objects.requireNonNull(value, "value");
		if (!value.equals(value.strip()) || value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
			throw new IllegalArgumentException("관심사 규칙을 어겼다");
		}
	}

	/** 입력을 읽는다 — 앞뒤 공백을 지우고 상한을 넘으면 빈 값 */
	public static Optional<InterestText> parse(String raw) {
		String stripped = Objects.requireNonNull(raw, "raw").strip();
		if (stripped.codePointCount(0, stripped.length()) > MAX_CODE_POINTS) {
			return Optional.empty();
		}
		return Optional.of(new InterestText(stripped));
	}

}
