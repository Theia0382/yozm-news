package net.yozmnews.event.domain.openai;

import java.util.Objects;

/**
 * OpenAI 호환 임베딩 API 키 — docs/specs/event.md §4 {@code domain/openai} · §7.
 * <p>
 * 요청 헤더에 실을 수 있는 값인지만 본다 — 키가 맞는지는 임베딩 때 드러난다. 값은 {@link #authorization()} 으로만 내보내고, 예외 문구 ·
 * {@link #toString()} 에 담지 않는다.
 */
public final class EmbeddingApiKey {

	/** 키의 최대 길이 */
	public static final int MAX_LENGTH = 512;

	private final String value;

	private EmbeddingApiKey(String value) {
		this.value = value;
	}

	/**
	 * @throws IllegalArgumentException 비었거나 {@value #MAX_LENGTH}자를 넘거나, 출력 가능한 ASCII(U+0021 ~ U+007E) 밖의 문자가
	 * 있을 때 — 문구에 값을 담지 않는다
	 */
	public static EmbeddingApiKey of(String value) {
		Objects.requireNonNull(value, "value");
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException(
					"임베딩 API 키는 1자 이상 " + MAX_LENGTH + "자 이하여야 한다 (지금 " + value.length() + "자)");
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < 0x21 || c > 0x7E) {
				throw new IllegalArgumentException("임베딩 API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
			}
		}
		return new EmbeddingApiKey(value);
	}

	/** {@code Authorization} 헤더 값 */
	public String authorization() {
		return "Bearer " + this.value;
	}

	@Override
	public String toString() {
		return "EmbeddingApiKey[숨김]";
	}

}
