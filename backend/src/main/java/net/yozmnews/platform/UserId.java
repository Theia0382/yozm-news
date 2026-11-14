package net.yozmnews.platform;

/**
 * 사용자 식별자 — docs/specs/platform.md §3 · platform-R24
 */
public record UserId(long value) {

	public UserId {
		if (value < 1) {
			throw new IllegalArgumentException("사용자 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
