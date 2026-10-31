package net.yozmnews.platform.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * 운영자 자격 — 운영자 기능이 열렸는지와 제시된 토큰이 맞는지의 판정. docs/specs/platform.md §4 · platform-R11.
 * <p>
 * 값은 다이제스트로만 갖고, 예외 메시지 · {@link #toString()} 에 담지 않는다.
 */
public final class AdminCredential {

	/** 토큰의 최소 길이 — 정본 */
	public static final int MIN_LENGTH = 32;

	/** 토큰의 최대 길이 */
	public static final int MAX_LENGTH = 512;

	/** RFC 6750 b64token */
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

	private static final AdminCredential CLOSED = new AdminCredential(null);

	private final byte @Nullable [] digest;

	private AdminCredential(byte @Nullable [] digest) {
		this.digest = digest;
	}

	/** 운영자 기능이 닫힌 자격 */
	public static AdminCredential closed() {
		return CLOSED;
	}

	/**
	 * 설정 값으로 자격을 만든다 — 없거나 빈 값이면 닫힘.
	 * @throws IllegalArgumentException 길이나 문자가 형식에 맞지 않을 때 — 메시지에 값을 담지 않는다
	 */
	public static AdminCredential of(@Nullable String configured) {
		if (configured == null || configured.isEmpty()) {
			return CLOSED;
		}
		if (configured.length() < MIN_LENGTH || configured.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("운영자 토큰은 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하여야 한다 (지금 "
					+ configured.length() + "자)");
		}
		if (!FORMAT.matcher(configured).matches()) {
			throw new IllegalArgumentException("운영자 토큰은 영문 대소문자 · 숫자 · - . _ ~ + / 와 끝의 = 만 쓸 수 있다");
		}
		return new AdminCredential(digest(configured));
	}

	/** 운영자 기능이 열렸는가 */
	public boolean open() {
		return this.digest != null;
	}

	/** 제시된 토큰이 맞는가 — 닫혔으면 언제나 아니다. 다이제스트를 상수 시간으로 견준다 */
	public boolean matches(@Nullable String presented) {
		if (this.digest == null || presented == null) {
			return false;
		}
		return MessageDigest.isEqual(this.digest, digest(presented));
	}

	@Override
	public String toString() {
		return open() ? "AdminCredential[open]" : "AdminCredential[closed]";
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

}
