package net.yozmnews.article.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SHA-256 다이제스트 32바이트 — 길이 상한이 없는 키(정규 URL · 제목 비교 키 · guid)를 인덱스와 비교에 쓰는 형태. 배열을
 * 가진 레코드는 equals 가 참조 비교라 클래스로 두고, 배열은 복사해 보관한다.
 */
public final class KeyHash {

	/** 다이제스트 바이트 수 */
	public static final int LENGTH = 32;

	private final byte[] bytes;

	private KeyHash(byte[] bytes) {
		this.bytes = bytes;
	}

	/** UTF-8 바이트의 SHA-256 */
	public static KeyHash of(String text) {
		try {
			return new KeyHash(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

	/** 저장해 둔 다이제스트 — 길이가 {@link #LENGTH} 가 아니면 거부한다 */
	public static KeyHash fromBytes(byte[] bytes) {
		if (bytes.length != LENGTH) {
			throw new IllegalArgumentException("다이제스트는 " + LENGTH + "바이트여야 한다: " + bytes.length);
		}
		return new KeyHash(bytes.clone());
	}

	/** 복사본 */
	public byte[] bytes() {
		return this.bytes.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof KeyHash that && Arrays.equals(this.bytes, that.bytes);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.bytes);
	}

	/** 16진수 소문자 */
	@Override
	public String toString() {
		return HexFormat.of().formatHex(this.bytes);
	}

}
