package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PasswordChangeMethod;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 알림 — 비밀번호가 바뀔 때마다 그 계정에 하나를 남긴다(모으지 않는다). 첫 로그인의 강제 변경은 부르지 않는다. docs/specs/user.md §4
 */
@Component
class PasswordChangedNotices {

	private final NoticeRepository notices;

	PasswordChangedNotices(NoticeRepository notices) {
		this.notices = Objects.requireNonNull(notices, "notices");
	}

	/** 부르는 쪽의 트랜잭션 안에서 알림 하나를 만든다 */
	void record(UserId user, PasswordChangeMethod method, Instant now) {
		this.notices.create(user, new PasswordChangedContent(method, now), now);
	}

	@Override
	public String toString() {
		return "PasswordChangedNotices";
	}

}
