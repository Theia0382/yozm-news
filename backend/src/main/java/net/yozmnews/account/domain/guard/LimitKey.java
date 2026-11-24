package net.yozmnews.account.domain.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import net.yozmnews.platform.GuardedAction;

/**
 * 요청 제한 기록의 키 — 동작 · 축 · 대상의 SHA-256 다이제스트. 대상(IP · 사용자 ID)을 평문으로 저장하지 않는다. docs/specs/user.md §4 ·
 * user-R39
 *
 * @param digest 32바이트
 */
public record LimitKey(byte[] digest) {

	/** 다이제스트 길이(바이트) */
	public static final int LENGTH = 32;

	public LimitKey {
		Objects.requireNonNull(digest, "digest");
		if (digest.length != LENGTH) {
			throw new IllegalArgumentException("다이제스트는 " + LENGTH + "바이트다: " + digest.length);
		}
		digest = digest.clone();
	}

	/** 동작 · 축 · 대상의 키 — 셋 가운데 하나만 달라도 다른 키다 */
	public static LimitKey of(GuardedAction action, LimitAxis axis, String subject) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(axis, "axis");
		Objects.requireNonNull(subject, "subject");
		try {
			return new LimitKey(MessageDigest.getInstance("SHA-256")
				.digest((action.name() + "\n" + axis.name() + "\n" + subject).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

	@Override
	public byte[] digest() {
		return this.digest.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LimitKey key && Arrays.equals(this.digest, key.digest);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.digest);
	}

	@Override
	public String toString() {
		return "LimitKey[" + HexFormat.of().formatHex(this.digest) + "]";
	}

}
