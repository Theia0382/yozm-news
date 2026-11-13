package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.NoticeRetention;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 읽은 알림 정리 유스케이스 — 읽은 지 보존 기간이 지난 알림을 지운다. 안 읽은 알림은 지우지 않는다. docs/specs/user.md §4 ·
 * user-R26
 */
@Service
public class NoticeCleanup {

	private final NoticeRepository notices;

	private final InstantSource clock;

	@Autowired
	NoticeCleanup(NoticeRepository notices) {
		this(notices, InstantSource.system());
	}

	NoticeCleanup(NoticeRepository notices, InstantSource clock) {
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 읽은 지 오래된 알림을 지운다.
	 * @return 지운 수
	 */
	public int run() {
		return this.notices.forgetReadBefore(NoticeRetention.forgetBefore(this.clock.instant()));
	}

}
