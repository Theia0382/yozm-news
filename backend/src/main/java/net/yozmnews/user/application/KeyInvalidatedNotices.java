package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.NoticeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키 무효 알림 유스케이스 — 키가 무효가 된 전이 하나에 그 사용자의 알림 하나를 남긴다. 같은 전이를 거듭 받아도 하나만 남는다.
 * docs/specs/user.md §4 · user-R36
 */
@Service
public class KeyInvalidatedNotices {

	private final NoticeRepository notices;

	private final InstantSource clock;

	@Autowired
	KeyInvalidatedNotices(NoticeRepository notices) {
		this(notices, InstantSource.system());
	}

	KeyInvalidatedNotices(NoticeRepository notices, InstantSource clock) {
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 전이 하나를 알림으로 남긴다 — 이벤트 하나가 트랜잭션 하나다 */
	@Transactional
	public void record(CredentialInvalidated invalidated) {
		this.notices.createOnce(invalidated.user(), new KeyInvalidatedContent(invalidated.provider(),
				invalidated.keyId(), invalidated.invalidatedAt()), this.clock.instant());
	}

}
