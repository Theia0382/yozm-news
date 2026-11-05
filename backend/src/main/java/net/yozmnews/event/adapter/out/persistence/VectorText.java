package net.yozmnews.event.adapter.out.persistence;

import java.util.Objects;

import net.yozmnews.event.domain.feature.Embedding;

/**
 * 벡터의 pgvector 텍스트 표현 — {@code [v1,v2,…]}, 값은 {@link Float#toString(float)}. SQL 이 {@code vector} 로 바꾼다.
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

}
