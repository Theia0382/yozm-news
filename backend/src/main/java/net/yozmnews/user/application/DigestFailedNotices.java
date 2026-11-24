package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.user.DigestFailed;
import net.yozmnews.user.domain.notice.DigestFailedContent;
import net.yozmnews.user.domain.notice.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다이제스트 실패 알림 유스케이스 — 실패한 다이제스트 하나에 그 사용자의 알림 하나를 남긴다. 같은 다이제스트를 거듭 받아도 하나만 남고, 계정이
 * 없으면 남기지 않는다. docs/specs/user.md §4
 */
@Service
public class DigestFailedNotices {

	private final AccountDirectory accounts;

	private final NoticeRepository notices;

	private final InstantSource clock;

	DigestFailedNotices(AccountDirectory accounts, NoticeRepository notices, InstantSource clock) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 실패 하나를 알림으로 남긴다 — 이벤트 하나가 트랜잭션 하나다 */
	@Transactional
	public void record(DigestFailed failed) {
		if (!this.accounts.exists(failed.user())) {
			return;
		}
		this.notices.createOnce(failed.user(), new DigestFailedContent(failed.digestId(), failed.digestDate(),
				failed.cause(), failed.failedAt()), this.clock.instant());
	}

}
