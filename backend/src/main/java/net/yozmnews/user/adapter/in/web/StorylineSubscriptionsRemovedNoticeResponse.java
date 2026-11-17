package net.yozmnews.user.adapter.in.web;

import java.util.List;
import java.util.Map;

import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import org.jspecify.annotations.Nullable;

/**
 * 사라진 구독 알림의 본문 — 구독에서 뺀 스토리라인마다 그 흐름의 대표 기사 제목. docs/specs/user.md §3 · user-R54
 * <p>
 * 제목은 알림함을 읽을 때 사건의 지금 대표 기사에서 읽은 값이다 — 알림에 저장하지 않는다.
 *
 * @param removed 사라진 구독 — 스토리라인 ID 순
 */
record StorylineSubscriptionsRemovedNoticeResponse(List<RemovedStorylineResponse> removed) {

	static StorylineSubscriptionsRemovedNoticeResponse of(StorylineSubscriptionsRemovedContent content,
			Map<Long, String> eventTitles) {
		return new StorylineSubscriptionsRemovedNoticeResponse(content.removed()
			.stream()
			.map(item -> new RemovedStorylineResponse(item.storylineId(),
					(item.eventId() == null) ? null : eventTitles.get(item.eventId())))
			.toList());
	}

	/**
	 * 사라진 구독 하나.
	 *
	 * @param storylineId 사라진 스토리라인
	 * @param title 그 흐름에 남았던 사건의 지금 대표 기사 제목 — 알 수 없거나 기사가 지워졌으면 null
	 */
	record RemovedStorylineResponse(long storylineId, @Nullable String title) {
	}

}
