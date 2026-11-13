package net.yozmnews.user.domain.credential;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * 비밀번호 규칙의 정본 — NFKC 로 정규화한 뒤 코드 포인트로 센 길이만 본다. 문자 종류 · 흔한 비밀번호 목록은 보지 않는다. docs/specs/user.md §4 ·
 * user-R14
 */
public final class PasswordPolicy {

	/** 최소 길이(코드 포인트) */
	public static final int MIN_CODE_POINTS = 8;

	/** 최대 길이(코드 포인트) — 해시 비용의 상한이기도 하다 */
	public static final int MAX_CODE_POINTS = 128;

	private PasswordPolicy() {
	}

	/** 해시 · 대조 · 길이 판정 전에 한 번 하는 정규화(NFKC) */
	public static String normalize(String raw) {
		return Normalizer.normalize(Objects.requireNonNull(raw, "raw"), Normalizer.Form.NFKC);
	}

	/**
	 * 정규화한 비밀번호가 규칙을 어긴 까닭.
	 * @return 규칙에 맞으면 비어 있다
	 */
	public static Optional<PasswordProblem> check(String normalized) {
		int length = codePoints(normalized);
		if (length < MIN_CODE_POINTS) {
			return Optional.of(PasswordProblem.TOO_SHORT);
		}
		if (length > MAX_CODE_POINTS) {
			return Optional.of(PasswordProblem.TOO_LONG);
		}
		return Optional.empty();
	}

	private static int codePoints(String normalized) {
		return normalized.codePointCount(0, normalized.length());
	}

}
