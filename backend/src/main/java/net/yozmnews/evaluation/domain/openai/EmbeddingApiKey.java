package net.yozmnews.evaluation.domain.openai;

import java.util.Objects;

import net.yozmnews.platform.HeaderValueShape;

/**
 * OpenAI 호환 임베딩 API 키 — docs/specs/evaluation.md §4 {@code domain/openai} · §7.
 * <p>
 * 요청 헤더에 실을 수 있는 값인지만 본다 — 키가 맞는지는 임베딩 때 드러난다. 값은 {@link #authorization()} 으로만 내보내고, 예외 문구 ·
 * {@link #toString()} 에 담지 않는다.
 */
public final class EmbeddingApiKey {

	private final String value;

	private EmbeddingApiKey(String value) {
		this.value = value;
	}

	/**
	 * @throws IllegalArgumentException 요청 헤더에 실을 수 있는 꼴({@link HeaderValueShape})이 아닐 때 — 문구에 값을 담지 않는다
	 */
	public static EmbeddingApiKey of(String value) {
		Objects.requireNonNull(value, "value");
		HeaderValueShape.violation(value).ifPresent(violation -> {
			throw new IllegalArgumentException(switch (violation) {
				case LENGTH -> "임베딩 API 키는 1자 이상 " + HeaderValueShape.MAX_LENGTH + "자 이하여야 한다 (지금 " + value.length()
						+ "자)";
				case CHARACTER -> "임베딩 API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다";
			});
		});
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
