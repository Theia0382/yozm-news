package net.yozmnews.user.domain.secret;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 복구 코드 — 계정마다 하나인 160비트 무작위 값. Crockford Base32 32자이고, 4자씩 {@code -} 로 이어 보인다. docs/specs/user.md §4.
 * <p>
 * 비교 · 저장은 정규형(대문자 32자)의 {@link SecretDigest} 로만 한다. 읽을 때 대소문자 · 공백 · {@code -} 를 무시하고, 헷갈리는 글자
 * {@code I} · {@code L} 은 {@code 1}, {@code O} 는 {@code 0} 으로 읽는다. {@link #toString()} 은 값을 숨긴다.
 */
public final class RecoveryCode {

	/** 무작위 비트 수 */
	public static final int BITS = 160;

	/** 정규형의 글자 수 — 5비트씩 */
	public static final int LENGTH = BITS / 5;

	/** 보일 때 한 묶음의 글자 수 */
	public static final int GROUP = 4;

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

	private final String value;

	private RecoveryCode(String value) {
		this.value = value;
	}

	/** 새 코드 */
	public static RecoveryCode generate(SecureRandom random) {
		byte[] bytes = new byte[BITS / 8];
		Objects.requireNonNull(random, "random").nextBytes(bytes);
		StringBuilder text = new StringBuilder(LENGTH);
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= 5) {
				bits -= 5;
				text.append(ALPHABET.charAt((buffer >> bits) & 0x1F));
			}
		}
		return new RecoveryCode(text.toString());
	}

	/**
	 * 입력을 코드로 읽는다.
	 * @return 정규형이 32자가 아니거나 Base32 밖의 글자가 있으면 비어 있다
	 */
	public static Optional<RecoveryCode> parse(String raw) {
		StringBuilder text = new StringBuilder(LENGTH);
		for (char c : Objects.requireNonNull(raw, "raw").toUpperCase(Locale.ROOT).toCharArray()) {
			if (c == '-' || Character.isWhitespace(c)) {
				continue;
			}
			char read = switch (c) {
				case 'I', 'L' -> '1';
				case 'O' -> '0';
				default -> c;
			};
			if (ALPHABET.indexOf(read) < 0) {
				return Optional.empty();
			}
			text.append(read);
		}
		return (text.length() == LENGTH) ? Optional.of(new RecoveryCode(text.toString())) : Optional.empty();
	}

	/** 보이는 꼴 — 4자씩 {@code -} 로 잇는다 */
	public String display() {
		StringBuilder text = new StringBuilder(LENGTH + LENGTH / GROUP);
		for (int i = 0; i < LENGTH; i += GROUP) {
			if (i > 0) {
				text.append('-');
			}
			text.append(this.value, i, i + GROUP);
		}
		return text.toString();
	}

	/** 저장 · 대조할 다이제스트 — 정규형의 다이제스트 */
	public SecretDigest digest() {
		return SecretDigest.of(this.value);
	}

	@Override
	public String toString() {
		return "RecoveryCode[숨김]";
	}

}
