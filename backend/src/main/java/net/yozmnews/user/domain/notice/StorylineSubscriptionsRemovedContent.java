package net.yozmnews.user.domain.notice;

import java.util.List;
import java.util.Objects;

/**
 * 사라진 구독 알림의 내용 — 구독한 스토리라인이 지워져 구독에서 뺀 항목. 모으지 않는다(따라가기 한 번마다 알림 하나).
 * docs/specs/user.md §4 · user-R54
 *
 * @param removed 사라진 구독 — 스토리라인 ID 순, 비지 않는다
 */
public record StorylineSubscriptionsRemovedContent(List<RemovedSubscription> removed) implements NoticeContent {

	/**
	 * @throws IllegalArgumentException 항목이 없을 때 — 사라진 구독이 없으면 알림을 남기지 않는다
	 */
	public StorylineSubscriptionsRemovedContent {
		removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
		if (removed.isEmpty()) {
			throw new IllegalArgumentException("사라진 구독이 없는 알림이다");
		}
	}

}
