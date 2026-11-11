package net.yozmnews.storyline.domain.linking;

/**
 * 두 벡터의 코사인 유사도 — docs/specs/storyline.md §4 · storyline-R8.
 */
public final class Cosine {

	private Cosine() {
	}

	/**
	 * 코사인 유사도 — 합 · 곱은 {@code double} 이다.
	 * @return 어느 한쪽이 영 벡터면 NaN(pgvector 의 코사인 거리와 같다)
	 * @throws IllegalArgumentException 길이가 다를 때
	 */
	public static double of(float[] a, float[] b) {
		if (a.length != b.length) {
			throw new IllegalArgumentException("벡터 길이가 다르다: " + a.length + " ≠ " + b.length);
		}
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			return Double.NaN;
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
