package net.yozmnews.user.application;

import net.yozmnews.user.ReportFailed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ReportFailed} 리스너 — 그 사용자에게 리포트 실패 알림을 남긴다. docs/specs/user.md §4
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 리스너는 발행한 프로세스와 발행을 재제출한 프로세스에서 돌므로, 이벤트 리스너는 두 역할
 * 공통이다(platform-R9).
 */
@Component
class ReportFailedNoticeListener {

	private final ReportFailedNotices notices;

	ReportFailedNoticeListener(ReportFailedNotices notices) {
		this.notices = notices;
	}

	@ApplicationModuleListener
	public void on(ReportFailed failed) {
		this.notices.record(failed);
	}

}
