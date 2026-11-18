package net.yozmnews.user.domain.subscription;

/**
 * 내 구독 이력 한 쪽의 행 수 — 정본. 측정 전 목표. docs/specs/user.md §5 · user-R63
 * <p>
 * 화면은 한 쪽의 스토리라인 제목을 카드 처리기 한 번으로 붙이므로 이 수는 카드 한 번의 상한
 * ({@code StorylineDirectory.MAX_PAGE_SIZE}) 이하여야 한다 — {@code SubscriptionPageLimitsTests} 가 지킨다.
 */
public final class StorylineSubscriptionHistoryPage {

	/** 한 쪽에 내는 이력 행 수 */
	public static final int SIZE = 50;

	private StorylineSubscriptionHistoryPage() {
	}

}
