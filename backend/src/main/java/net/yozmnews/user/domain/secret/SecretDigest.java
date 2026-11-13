package net.yozmnews.user.domain.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 높은 엔트로피 비밀(운영자 토큰 · 초대 토큰 · 복구 코드)의 SHA-256 다이제스트 — 저장 · 대조의 정본. docs/specs/user.md §4.
 * <p>
 * 비밀은 추측할 수 없는 길이라 느린 해시가 필요 없다. 비밀 자체는 갖지 않고, 대조는 상수 시간이다. {@link #toString()} 은 앞 4바이트만 보인다.
 */
public final class SecretDigest {

	/** 다이제스트 길이(바이트) */
	public static final int LENGTH = 32;

	private final byte[] value;

	private SecretDigest(byte[] value) {
		this.value = value;
	}

	/** 비밀의 다이제스트 — UTF-8 바이트의 SHA-256 */
	public static SecretDigest of(String secret) {
		try {
			return new SecretDigest(MessageDigest.getInstance("SHA-256")
				.digest(Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

	/**
	 * 저장된 다이제스트를 읽는다.
	 * @throws IllegalArgumentException 길이가 {@link #LENGTH} 가 아닐 때
	 */
	public static SecretDigest stored(byte[] value) {
		Objects.requireNonNull(value, "value");
		if (value.length != LENGTH) {
			throw new IllegalArgumentException("다이제스트는 " + LENGTH + "바이트다: " + value.length);
		}
		return new SecretDigest(value.clone());
	}

	/** 같은 비밀의 다이제스트인가 — 상수 시간 */
	public boolean matches(SecretDigest other) {
		return MessageDigest.isEqual(this.value, Objects.requireNonNull(other, "other").value);
	}

	/** 저장할 바이트 */
	public byte[] value() {
		return this.value.clone();
	}

	@Override
	public String toString() {
		return "SecretDigest[" + HexFormat.of().formatHex(this.value, 0, 4) + "…]";
	}

}
