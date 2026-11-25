package net.yozmnews.user.domain.subscription;

/**
 * 사용자당 흐름 추적(스토리라인 구독) 상한 — 정본. 측정 전 목표. docs/specs/user.md §5 · user-R52
 */
public final class StorylineSubscriptionLimit {

	/** 한 사용자가 추적할 수 있는 사용자 스토리라인 수 — 새로 추적할 때 넘으면 거부한다. 추적 중인 사용자 스토리라인이 갈라져 넘는 것은 둔다 */
	public static final int MAX = 100;

	private StorylineSubscriptionLimit() {
	}

}
