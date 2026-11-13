package net.yozmnews.user.domain.secret;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 초대 토큰 — 128비트 무작위 값의 base64url(채움 없음) 22자. 링크의 조각({@code #})에 실려 전해지고, 저장 · 대조는 {@link SecretDigest} 로만
 * 한다. docs/specs/user.md §4. {@link #toString()} 은 값을 숨긴다.
 */
public final class InvitationToken {

	/** 무작위 비트 수 */
	public static final int BITS = 128;

	/** 글자 수 */
	public static final int LENGTH = 22;

	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");

	private final String value;

	private InvitationToken(String value) {
		this.value = value;
	}

	/** 새 토큰 */
	public static InvitationToken generate(SecureRandom random) {
		byte[] bytes = new byte[BITS / 8];
		Objects.requireNonNull(random, "random").nextBytes(bytes);
		return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

	/**
	 * 입력을 토큰으로 읽는다 — 앞뒤 공백은 지운다.
	 * @return 22자의 base64url 이 아니면 비어 있다
	 */
	public static Optional<InvitationToken> parse(String raw) {
		String stripped = Objects.requireNonNull(raw, "raw").strip();
		return FORMAT.matcher(stripped).matches() ? Optional.of(new InvitationToken(stripped)) : Optional.empty();
	}

	/** 링크에 싣는 값 — 발급 응답에 한 번만 나간다 */
	public String value() {
		return this.value;
	}

	/** 저장 · 대조할 다이제스트 */
	public SecretDigest digest() {
		return SecretDigest.of(this.value);
	}

	@Override
	public String toString() {
		return "InvitationToken[숨김]";
	}

}
