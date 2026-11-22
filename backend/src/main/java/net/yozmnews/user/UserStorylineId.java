package net.yozmnews.user;

/**
 * 사용자 스토리라인 식별자 — {@code user_storylines.id} · docs/specs/user.md §3
 */
public record UserStorylineId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public UserStorylineId {
		if (value < 1) {
			throw new IllegalArgumentException("사용자 스토리라인 ID 는 1 이상이다: " + value);
		}
	}

}
