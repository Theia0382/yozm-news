package net.yozmnews.user.application;

import net.yozmnews.credential.CredentialInvalidated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link CredentialInvalidated} 리스너 — 그 사용자에게 키 무효 알림을 남긴다. docs/specs/user.md §4 · user-R36
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 키가 무효임은 화면 역할의 다시 검증에서도, 작업 역할의 호출에서도 드러난다.
 */
@Component
class KeyInvalidatedNoticeListener {

	private final KeyInvalidatedNotices notices;

	KeyInvalidatedNoticeListener(KeyInvalidatedNotices notices) {
		this.notices = notices;
	}

	@ApplicationModuleListener
	public void on(CredentialInvalidated invalidated) {
		this.notices.record(invalidated);
	}

}
