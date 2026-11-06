package net.yozmnews.event.domain.assignment;

/**
 * 사건 중심 벡터의 누적 갱신 정본 — 중심은 구성 기사 벡터의 평균이다 · docs/specs/event.md §5.
 */
public final class CentroidUpdate {

	private CentroidUpdate() {
	}

	/**
	 * 기사 하나를 더한 중심 — {@code (c × n + v) / (n + 1)}.
	 * @param count 지금 중심을 이룬 기사 수 — 1 이상
	 * @throws IllegalArgumentException 수가 1 미만이거나 벡터 길이가 다를 때
	 */
	public static float[] add(float[] centroid, int count, float[] vector) {
		if (count < 1) {
			throw new IllegalArgumentException("중심을 이룬 기사 수는 1 이상이다: " + count);
		}
		return combine(centroid, count, vector, 1, count + 1);
	}

	/**
	 * 기사 하나를 뺀 중심 — {@code (c × n − v) / (n − 1)}.
	 * @param count 지금 중심을 이룬 기사 수 — 2 이상(하나뿐이면 사건을 지운다)
	 * @throws IllegalArgumentException 수가 2 미만이거나 벡터 길이가 다를 때
	 */
	public static float[] remove(float[] centroid, int count, float[] vector) {
		if (count < 2) {
			throw new IllegalArgumentException("기사를 빼려면 중심을 이룬 기사가 둘 이상이어야 한다: " + count);
		}
		return combine(centroid, count, vector, -1, count - 1);
	}

	private static float[] combine(float[] centroid, int count, float[] vector, int sign, int nextCount) {
		if (centroid.length != vector.length) {
			throw new IllegalArgumentException("벡터 길이가 다르다: 중심 " + centroid.length + " · 기사 " + vector.length);
		}
		float[] next = new float[centroid.length];
		for (int i = 0; i < centroid.length; i++) {
			next[i] = (float) (((double) centroid[i] * count + sign * (double) vector[i]) / nextCount);
		}
		return next;
	}

}
