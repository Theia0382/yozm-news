package net.yozmnews.account.domain.humancheck;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 봇 차단 위젯이 준 사람 확인 토큰 — 화면이 요청 헤더로 보낸다. 한 번만 검증된다. docs/specs/user.md §4 · user-R41
 * <p>
 * 문자열 표현은 값을 숨긴다 — 토큰은 로그 · 문구에 남기지 않는다.
 *
 * @param value 1 ~ {@value #MAX_LENGTH} 자
 */
public record HumanCheckToken(String value) {

	/** 토큰 길이 상한 — Turnstile 의 규격 */
	public static final int MAX_LENGTH = 2048;

	public HumanCheckToken {
		Objects.requireNonNull(value, "value");
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("사람 확인 토큰은 1 ~ " + MAX_LENGTH + "자다");
		}
	}

	/**
	 * 요청 헤더 값을 읽는다.
	 * @return 없거나 비었거나 길이 상한을 넘으면 비어 있다
	 */
	public static Optional<HumanCheckToken> parse(@Nullable String raw) {
		return (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH) ? Optional.empty()
				: Optional.of(new HumanCheckToken(raw));
	}

	@Override
	public String toString() {
		return "HumanCheckToken[숨김]";
	}

}
