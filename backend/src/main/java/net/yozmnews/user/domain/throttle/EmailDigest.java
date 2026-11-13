package net.yozmnews.user.domain.throttle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import net.yozmnews.user.domain.account.EmailAddress;

/**
 * 실패 기록의 키 — 정규화한 이메일의 SHA-256 다이제스트(열쇠 없음). docs/specs/user.md §4 · user-R19
 * <p>
 * 이메일 원문을 담지 않는다. 문자열 표현에도 다이제스트를 싣지 않는다.
 */
public final class EmailDigest {

	private final byte[] value;

	private EmailDigest(byte[] value) {
		this.value = value;
	}

	/** 정규화한 이메일({@code EmailAddress.normalize} 의 결과)의 다이제스트 */
	public static EmailDigest of(String normalizedEmail) {
		try {
			return new EmailDigest(MessageDigest.getInstance("SHA-256")
				.digest(Objects.requireNonNull(normalizedEmail, "normalizedEmail").getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 이 없다", ex);
		}
	}

	/**
	 * 제출된 이메일의 다이제스트 — 앞뒤 공백을 지운 소문자({@link EmailAddress#normalize}). 이메일로 읽을 수 없는 입력도 같은 변환으로 센다
	 * (user-R19). 실패 기록의 키를 만드는 정본이다
	 */
	public static EmailDigest ofSubmitted(String rawEmail) {
		return of(EmailAddress.normalize(Objects.requireNonNull(rawEmail, "rawEmail")));
	}

	/** 다이제스트 바이트 — 복사본 */
	public byte[] value() {
		return this.value.clone();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof EmailDigest digest) && Arrays.equals(this.value, digest.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.value);
	}

	@Override
	public String toString() {
		return "EmailDigest[" + HexFormat.of().formatHex(this.value, 0, 4) + "…]";
	}

}
