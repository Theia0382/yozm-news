package net.yozmnews.storyline;

/**
 * 스토리라인 식별자 — {@code storylines.id} · docs/specs/storyline.md §3
 */
public record StorylineId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public StorylineId {
		if (value < 1) {
			throw new IllegalArgumentException("스토리라인 ID 는 1 이상이다: " + value);
		}
	}

}
