package net.yozmnews.user.domain.account;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.CodePoints;

/**
 * 표시 이름 — 규칙의 정본. 앞뒤 공백을 지운 1 ~ 50 코드 포인트다. docs/specs/user.md §4.
 *
 * @param value 앞뒤 공백을 지운 표시 이름
 */
public record DisplayName(String value) {

	/** 최대 길이(코드 포인트) */
	public static final int MAX_CODE_POINTS = 50;

	public DisplayName {
		Objects.requireNonNull(value, "value");
		if (!value.equals(value.strip()) || !fits(value)) {
			throw new IllegalArgumentException("표시 이름이 아니다");
		}
	}

	/**
	 * 입력을 표시 이름으로 읽는다.
	 * @return 앞뒤 공백을 지우면 비거나 50 코드 포인트를 넘으면 비어 있다
	 */
	public static Optional<DisplayName> parse(String raw) {
		String stripped = Objects.requireNonNull(raw, "raw").strip();
		return fits(stripped) ? Optional.of(new DisplayName(stripped)) : Optional.empty();
	}

	/** 이메일의 {@code @} 앞부분 — 표시 이름을 받지 않고 만드는 계정(첫 운영자)의 것. 50 코드 포인트에서 자른다 */
	public static DisplayName ofEmail(EmailAddress email) {
		String local = email.value().substring(0, email.value().indexOf('@'));
		return new DisplayName(CodePoints.prefix(local, MAX_CODE_POINTS).strip());
	}

	private static boolean fits(String stripped) {
		int length = stripped.codePointCount(0, stripped.length());
		return length >= 1 && length <= MAX_CODE_POINTS;
	}

}
