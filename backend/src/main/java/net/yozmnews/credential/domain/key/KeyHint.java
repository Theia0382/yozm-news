package net.yozmnews.credential.domain.key;

import java.util.Objects;

import net.yozmnews.platform.SecretValue;

/**
 * 힌트 규칙 — 정본 · docs/specs/credential.md §4 · credential-R9
 */
public final class KeyHint {

	/** 힌트를 내는 키의 최소 길이(문자) — 이보다 짧으면 힌트가 없다 */
	public static final int MIN_LENGTH = 20;

	/** 힌트의 길이(문자) — 키의 끝에서 */
	static final int TAIL = 4;

	private KeyHint() {
	}

	/**
	 * @return 키의 끝 {@value #TAIL} 자 — 키가 {@value #MIN_LENGTH} 자보다 짧으면 빈 문자열
	 */
	public static String of(SecretValue key) {
		Objects.requireNonNull(key, "key");
		if (key.length() < MIN_LENGTH) {
			return "";
		}
		String value = key.reveal();
		return value.substring(value.length() - TAIL);
	}

}
