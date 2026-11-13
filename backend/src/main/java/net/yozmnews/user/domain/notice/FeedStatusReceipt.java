package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드 상태 알림의 반영 기준 판정 — 정본. docs/specs/user.md §4 · user-R24
 * <p>
 * 반영 기준은 사용자마다 반영한 마지막 검사 회차의 판정 시각이다. 같은 회차가 다시 처리되거나 앞선 회차가 늦게 오면 거른다 — 늦게 온
 * 앞선 회차는 버리므로 바뀐 횟수 · 처음 상태가 빠질 수 있지만 마지막 새 상태는 맞다.
 */
public final class FeedStatusReceipt {

	private FeedStatusReceipt() {
	}

	/**
	 * 이 회차를 반영하는가.
	 * @param receivedThrough 반영 기준 — 반영한 회차가 없으면 null
	 * @param judgedAt 회차의 판정 시각
	 * @return 기준이 없거나 판정 시각이 기준보다 늦을 때만 참
	 */
	public static boolean accepts(@Nullable Instant receivedThrough, Instant judgedAt) {
		Objects.requireNonNull(judgedAt, "judgedAt");
		return receivedThrough == null || judgedAt.isAfter(receivedThrough);
	}

}
