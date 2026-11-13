package net.yozmnews.user.domain.notice;

import java.time.Duration;
import java.time.Instant;

/**
 * 알림 보존 · 알림함 쪽 크기의 정본 — docs/specs/user.md §4 · user-R26 · user-R27
 */
public final class NoticeRetention {

	/** 읽은 알림은 읽은 지 이 기간이 지나면 지운다 — 안 읽은 알림은 지우지 않는다 */
	public static final Duration KEEP_READ = Duration.ofDays(90);

	/** 알림함 한 쪽의 알림 수 — 읽음 요청 한 번의 상한이기도 하다 */
	public static final int PAGE_SIZE = 50;

	private NoticeRetention() {
	}

	/** 읽은 시각이 이 값보다 이른 알림을 지운다 — 정확히 보존 기간이 된 알림은 남는다 */
	public static Instant forgetBefore(Instant now) {
		return now.minus(KEEP_READ);
	}

}
