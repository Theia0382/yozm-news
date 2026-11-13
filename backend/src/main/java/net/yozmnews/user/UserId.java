package net.yozmnews.user;

/**
 * 사용자 식별자 — docs/specs/user.md §3 · user-R6
 */
public record UserId(long value) {

	public UserId {
		if (value < 1) {
			throw new IllegalArgumentException("사용자 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
