package net.yozmnews.storyline.domain.relation;

/**
 * 고리를 맺은 쌍 — 나란한 흐름으로 판정된 두 스토리라인 · docs/specs/storyline.md §4.
 * <p>
 * 상위를 열 때 맺은 포함 고리(상위 → 하위)는 그 고리의 두 끝이 아니라 나란한 흐름 쌍의 판정이 맺는다. 이 표지가 있는 동안 그 고리의 두 끝은
 * 따로 묻지 않고, 나란한 흐름 쌍의 판정이 바뀌면 표지를 지워 고리의 두 끝이 스스로 판정받게 한다.
 *
 * @param low 쌍의 작은 ID
 * @param high 쌍의 큰 ID
 */
public record GroupedBy(long low, long high) {

	/**
	 * @throws IllegalArgumentException 두 ID 가 순서대로가 아닐 때
	 */
	public GroupedBy {
		if (low >= high) {
			throw new IllegalArgumentException("맺은 쌍은 작은 ID · 큰 ID 순이다: " + low + " · " + high);
		}
	}

	/** 두 스토리라인의 쌍 — 순서와 무관 */
	public static GroupedBy of(long a, long b) {
		return new GroupedBy(Math.min(a, b), Math.max(a, b));
	}

	/** 그 두 스토리라인의 쌍인가 — 순서와 무관 */
	public boolean is(long a, long b) {
		return this.low == Math.min(a, b) && this.high == Math.max(a, b);
	}

}
