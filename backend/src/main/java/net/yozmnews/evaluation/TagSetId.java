package net.yozmnews.evaluation;

/**
 * 태그 체계 버전 식별자 — {@code tag_sets.id} — docs/specs/evaluation.md §3
 */
public record TagSetId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public TagSetId {
		if (value < 1) {
			throw new IllegalArgumentException("태그 체계 버전 ID 는 1 이상이다: " + value);
		}
	}

}
