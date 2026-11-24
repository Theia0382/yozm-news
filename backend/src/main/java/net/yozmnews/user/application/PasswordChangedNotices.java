package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.user.PasswordChanged;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 알림 유스케이스 — 비밀번호 바뀜 하나에 그 사용자의 알림 하나를 남긴다(모으지 않는다). 같은 변경을 거듭 받아도 하나만 남는다.
 * docs/specs/user.md §4 · user-R74
 */
@Service
class PasswordChangedNotices {

	private final NoticeRepository notices;

	private final InstantSource clock;

	PasswordChangedNotices(NoticeRepository notices, InstantSource clock) {
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 변경 하나를 알림으로 남긴다 — 이벤트 하나가 트랜잭션 하나다 */
	@Transactional
	void record(PasswordChanged changed) {
		this.notices.createOnce(changed.user(), new PasswordChangedContent(changed.method(), changed.changedAt()),
				this.clock.instant());
	}

	@Override
	public String toString() {
		return "PasswordChangedNotices";
	}

}
