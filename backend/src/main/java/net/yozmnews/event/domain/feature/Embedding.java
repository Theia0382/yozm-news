package net.yozmnews.event.domain.feature;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 벡터 하나 — 값은 모두 유한한 {@code float}, 길이 1 이상 · docs/specs/event.md §4.
 * <p>
 * 배열은 복사해 받고 복사해 낸다. 같은 값이면 같다.
 */
public final class Embedding {

	private final float[] values;

	private Embedding(float[] values) {
		this.values = values;
	}

	/**
	 * @throws IllegalArgumentException 빈 배열이거나 NaN · 무한이 있을 때
	 */
	public static Embedding of(float[] values) {
		Objects.requireNonNull(values, "values");
		if (values.length == 0) {
			throw new IllegalArgumentException("벡터가 비었다");
		}
		float[] copy = values.clone();
		for (int i = 0; i < copy.length; i++) {
			if (!Float.isFinite(copy[i])) {
				throw new IllegalArgumentException("벡터 값이 유한하지 않다 — " + i + "번째: " + copy[i]);
			}
		}
		return new Embedding(copy);
	}

	/**
	 * L2 길이 1 로 나눈 벡터 — 정규화의 정본 · docs/specs/event.md §4. 합 · 나눗셈은 {@code double}, 결과는 {@code float} 다.
	 * @throws IllegalArgumentException 빈 배열이거나 NaN · 무한이 있거나 영벡터일 때
	 */
	public static Embedding normalized(float[] values) {
		Objects.requireNonNull(values, "values");
		if (values.length == 0) {
			throw new IllegalArgumentException("벡터가 비었다");
		}
		double sum = 0;
		for (int i = 0; i < values.length; i++) {
			if (!Float.isFinite(values[i])) {
				throw new IllegalArgumentException("벡터 값이 유한하지 않다 — " + i + "번째: " + values[i]);
			}
			sum += (double) values[i] * values[i];
		}
		double norm = Math.sqrt(sum);
		if (norm == 0) {
			throw new IllegalArgumentException("영벡터는 정규화할 수 없다");
		}
		float[] unit = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			unit[i] = (float) (values[i] / norm);
		}
		return of(unit);
	}

	public int dimensions() {
		return this.values.length;
	}

	/** 값의 복사본 */
	public float[] values() {
		return this.values.clone();
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other) || (other instanceof Embedding embedding && Arrays.equals(this.values, embedding.values));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.values);
	}

	@Override
	public String toString() {
		return "Embedding[dimensions=" + this.values.length + "]";
	}

}
