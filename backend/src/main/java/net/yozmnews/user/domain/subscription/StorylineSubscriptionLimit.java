package net.yozmnews.user.domain.subscription;

/**
 * 사용자당 스토리라인 구독 상한 — 정본. 측정 전 목표. docs/specs/user.md §5 · user-R52
 */
public final class StorylineSubscriptionLimit {

	/** 한 사용자가 가질 수 있는 구독 수 — 넘는 구독은 거부한다 */
	public static final int MAX = 100;

	private StorylineSubscriptionLimit() {
	}

}
