package net.yozmnews.user.application;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시 · 대조 — Argon2id. docs/specs/user.md §4 · user-R15 · user-R17
 * <p>
 * 받는 비밀번호는 {@code PasswordPolicy.normalize} 로 정규화한 값이다. 비밀번호 · 해시를 로그 · 예외 문구에 싣지 않는다.
 */
@Component
public class PasswordHashing {

	/** 솔트 길이(바이트) */
	static final int SALT_LENGTH = 16;

	/** 해시 길이(바이트) */
	static final int HASH_LENGTH = 32;

	/** 병렬 */
	static final int PARALLELISM = 1;

	/** 메모리(KiB) */
	static final int MEMORY_KIB = 19_456;

	/** 반복 */
	static final int ITERATIONS = 2;

	private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM,
			MEMORY_KIB, ITERATIONS);

	/** 아무 계정의 것도 아닌 해시 — 같은 파라미터라 대조 계산량이 실제 해시와 같다 */
	private final String nobody;

	PasswordHashing() {
		byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		this.nobody = this.encoder.encode(HexFormat.of().formatHex(random));
	}

	/** 정규화한 비밀번호의 해시 — 파라미터를 담은 문자열 */
	public String hash(String normalized) {
		return this.encoder.encode(Objects.requireNonNull(normalized, "normalized"));
	}

	/** 정규화한 비밀번호가 해시와 맞는가 */
	public boolean matches(String normalized, String hash) {
		return this.encoder.matches(Objects.requireNonNull(normalized, "normalized"),
				Objects.requireNonNull(hash, "hash"));
	}

	/**
	 * 계정이 없거나 쓰지 않는 계정일 때의 대조 — 더미 해시와 한 번 대조해 있는 계정과 같은 계산을 하고 언제나 거짓이다(user-R17).
	 */
	public boolean matchesNobody(String normalized) {
		this.encoder.matches(Objects.requireNonNull(normalized, "normalized"), this.nobody);
		return false;
	}

	@Override
	public String toString() {
		return "PasswordHashing[argon2id]";
	}

}
