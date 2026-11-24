package net.yozmnews.account.domain.guard;

import java.time.Duration;
import java.util.Objects;

/**
 * 요청 제한 규칙 하나 — 한 축의 대상마다 창 하나에 받는 요청 수. docs/specs/user.md §4 · user-R38
 *
 * @param axis 세는 축
 * @param maxRequests 창 하나에 받는 요청 수 — 1 이상
 * @param window 창 길이 — 1초 이상이고 하루를 나누어떨어지게 한다(창 경계가 날마다 같다)
 */
public record LimitRule(LimitAxis axis, int maxRequests, Duration window) {

	private static final Duration DAY = Duration.ofDays(1);

	public LimitRule {
		Objects.requireNonNull(axis, "axis");
		Objects.requireNonNull(window, "window");
		if (maxRequests < 1) {
			throw new IllegalArgumentException("받는 요청 수는 1 이상이다: " + maxRequests);
		}
		if (window.compareTo(Duration.ofSeconds(1)) < 0 || window.getNano() != 0
				|| DAY.toSeconds() % window.toSeconds() != 0) {
			throw new IllegalArgumentException("창 길이는 하루를 나누어떨어지게 하는 1초 이상의 초 단위 길이다: " + window);
		}
	}

}
