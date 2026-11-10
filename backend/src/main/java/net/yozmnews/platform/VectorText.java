package net.yozmnews.platform;

import java.util.Objects;

/**
 * 벡터의 pgvector 텍스트 표현 — {@code [v1,v2,…]}, 값은 {@link Float#toString(float)} · docs/specs/platform.md §3.
 * <p>
 * SQL 이 이 텍스트를 {@code vector} · {@code halfvec} 로 바꾸고, {@code ::text} 로 되읽은 값을 {@link #parse} 가 읽는다.
 * {@link Float#toString(float)} 의 지수 표기({@code 1.0E-5})는 pgvector 의 입력 파서가 받는다.
 */
public final class VectorText {

	private VectorText() {
	}

	/**
	 * 벡터를 텍스트 표현으로 쓴다.
	 * @throws IllegalArgumentException 빈 배열이거나 NaN · 무한이 있을 때
	 */
	public static String of(float[] values) {
		Objects.requireNonNull(values, "values");
		if (values.length == 0) {
			throw new IllegalArgumentException("벡터가 비었다");
		}
		StringBuilder text = new StringBuilder(values.length * 12).append('[');
		for (int i = 0; i < values.length; i++) {
			if (!Float.isFinite(values[i])) {
				throw new IllegalArgumentException("벡터 값이 유한하지 않다: " + i + "번째 " + values[i]);
			}
			if (i > 0) {
				text.append(',');
			}
			text.append(Float.toString(values[i]));
		}
		return text.append(']').toString();
	}

	/**
	 * pgvector 의 텍스트 출력을 읽는다.
	 * @throws IllegalArgumentException {@code [ … ]} 꼴이 아니거나 값이 비었을 때
	 */
	public static float[] parse(String text) {
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
