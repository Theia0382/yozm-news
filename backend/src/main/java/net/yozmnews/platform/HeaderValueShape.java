package net.yozmnews.platform;

import java.util.Objects;
import java.util.Optional;

/**
 * 요청 헤더에 실을 값의 꼴 — docs/specs/platform.md §3 · platform-R23.
 * <p>
 * 값이 무엇인지는 보지 않는다. 어긴 것을 어떤 문구 · 결과로 낼지는 쓰는 쪽이 정한다.
 */
public final class HeaderValueShape {

	/** 값의 최대 길이(문자 수) */
	public static final int MAX_LENGTH = 512;

	private static final char FIRST_PRINTABLE = 0x21;

	private static final char LAST_PRINTABLE = 0x7E;

	private HeaderValueShape() {
	}

	/**
	 * 꼴에 맞으면 빈 값. 길이를 먼저 본다 — 둘 다 어기면 {@link Violation#LENGTH} 다.
	 */
	public static Optional<Violation> violation(String value) {
		Objects.requireNonNull(value, "value");
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			return Optional.of(Violation.LENGTH);
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < FIRST_PRINTABLE || c > LAST_PRINTABLE) {
				return Optional.of(Violation.CHARACTER);
			}
		}
		return Optional.empty();
	}

	/** 꼴을 어긴 까닭 */
	public enum Violation {

		/** 비었거나 {@value HeaderValueShape#MAX_LENGTH}자를 넘는다 */
		LENGTH,
		/** 출력 가능한 ASCII(U+0021 ~ U+007E) 밖의 문자가 있다 */
		CHARACTER

	}

}
