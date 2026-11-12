package net.yozmnews.user.domain.account;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 정규화한 이메일 — 정규화의 정본. docs/specs/user.md §4 · user-R13
 * <p>
 * 앞뒤 공백을 지우고 로캘과 무관하게 소문자로 바꾼 값이다. 254 자 이하이고 {@code @} 가 정확히 하나이며 그 앞뒤가 비지 않는다.
 *
 * @param value 정규화한 이메일
 */
public record EmailAddress(String value) {

	/** 정규화한 이메일의 최대 길이(문자) */
	public static final int MAX_LENGTH = 254;

	public EmailAddress {
		Objects.requireNonNull(value, "value");
		if (!value.equals(normalize(value)) || !wellFormed(value)) {
			throw new IllegalArgumentException("정규화한 이메일이 아니다");
		}
	}

	/**
	 * 입력을 정규화해 이메일로 읽는다.
	 * @return 이메일로 읽을 수 없으면 비어 있다
	 */
	public static Optional<EmailAddress> parse(String raw) {
		String normalized = normalize(Objects.requireNonNull(raw, "raw"));
		return wellFormed(normalized) ? Optional.of(new EmailAddress(normalized)) : Optional.empty();
	}

	/** 앞뒤 공백을 지우고 소문자로 — 이메일로 읽을 수 없는 입력에도 같은 변환을 쓴다(user-R19) */
	public static String normalize(String raw) {
		return raw.strip().toLowerCase(Locale.ROOT);
	}

	private static boolean wellFormed(String normalized) {
		int at = normalized.indexOf('@');
		return normalized.length() <= MAX_LENGTH && at > 0 && at == normalized.lastIndexOf('@')
				&& at < normalized.length() - 1;
	}

}
