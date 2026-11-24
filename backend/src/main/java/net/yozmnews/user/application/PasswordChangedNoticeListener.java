package net.yozmnews.user.application;

import java.util.Objects;

import net.yozmnews.account.PasswordChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordChanged} 리스너 — 그 사용자에게 비밀번호 알림을 남긴다. docs/specs/user.md §4 · user-R74
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 비밀번호는 화면 역할의 처리기에서 바뀌지만, 이벤트를 낸 프로세스에 리스너가 없으면 발행이
 * 기록되지 않는다.
 */
@Component
class PasswordChangedNoticeListener {

	private final PasswordChangedNotices notices;

	PasswordChangedNoticeListener(PasswordChangedNotices notices) {
		this.notices = Objects.requireNonNull(notices, "notices");
	}

	@ApplicationModuleListener
	public void on(PasswordChanged changed) {
		this.notices.record(changed);
	}

}
