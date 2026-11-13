package net.yozmnews.user.application;

import net.yozmnews.source.FeedStatusesChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link FeedStatusesChanged} 리스너 — 운영자 계정에 피드 상태 알림을 남긴다. docs/specs/user.md §4 · user-R22
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 발행은 작업 역할의 피드 건강 검사가 한다.
 */
@Component
class FeedStatusNoticeListener {

	private final FeedStatusNotices notices;

	FeedStatusNoticeListener(FeedStatusNotices notices) {
		this.notices = notices;
	}

	@ApplicationModuleListener
	public void on(FeedStatusesChanged changed) {
		this.notices.record(changed);
	}

}
