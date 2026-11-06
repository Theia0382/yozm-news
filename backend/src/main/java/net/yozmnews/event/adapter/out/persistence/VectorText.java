package net.yozmnews.event.adapter.out.persistence;

import java.util.Objects;

import net.yozmnews.event.domain.feature.Embedding;

/**
 * 벡터의 pgvector 텍스트 표현 — {@code [v1,v2,…]}, 값은 {@link Float#toString(float)}. SQL 이 {@code vector} · {@code halfvec}
 * 로 바꾸고, {@code ::text} 로 되읽은 값을 {@link #parse} 가 읽는다.
 * <p>
 * {@link Float#toString(float)} 의 지수 표기({@code 1.0E-5})는 pgvector 의 입력 파서가 받는다. 값은 {@link Embedding} 이 유한한 값만
 * 받는다.
 */
final class VectorText {

	private VectorText() {
	}

	static String of(Embedding embedding) {
		Objects.requireNonNull(embedding, "embedding");
		float[] values = embedding.values();
		StringBuilder text = new StringBuilder(values.length * 12).append('[');
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				text.append(',');
			}
			text.append(Float.toString(values[i]));
		}
		return text.append(']').toString();
	}

	/**
	 * @throws IllegalArgumentException 빈 배열이거나 NaN · 무한이 있을 때
	 */
	static String of(float[] values) {
		return of(Embedding.of(values));
	}

	/**
	 * pgvector 의 텍스트 출력을 읽는다.
	 * @throws IllegalArgumentException {@code [ … ]} 꼴이 아니거나 값이 비었을 때
	 */
	static float[] parse(String text) {
		Objects.requireNonNull(text, "text");
		if (text.length() < 3 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
			throw new IllegalArgumentException("벡터 텍스트가 아니다: " + text);
		}
		String[] parts = text.substring(1, text.length() - 1).split(",");
		float[] values = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Float.parseFloat(parts[i]);
		}
		return values;
	}

}
