package net.yozmnews.account.domain.guard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 요청 제한 판정 — 받는다 · 넘었다. docs/specs/user.md §4 · user-R39
 */
public sealed interface LimitDecision {

	/** 한도 안이다 */
	record Allowed() implements LimitDecision {

	}

	/**
	 * 한도를 넘었다.
	 * @param retryAt 다시 받는 때 — 넘은 창의 끝
	 */
	record Exceeded(Instant retryAt) implements LimitDecision {

		public Exceeded {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

	/**
	 * 한 규칙의 판정 — 이 요청까지 센 수가 한도보다 크면 넘었다.
	 * @param counted 이 요청을 더한 창의 요청 수
	 */
	static LimitDecision of(int counted, LimitRule rule, LimitWindow window) {
		Objects.requireNonNull(rule, "rule");
		Objects.requireNonNull(window, "window");
		return (counted > rule.maxRequests()) ? new Exceeded(window.end()) : new Allowed();
	}

	/** 여러 규칙의 판정을 하나로 — 넘은 것이 있으면 가장 늦게 풀리는 것, 없으면 받는다 */
	static LimitDecision strictest(List<LimitDecision> decisions) {
		Objects.requireNonNull(decisions, "decisions");
		Instant latest = null;
		for (LimitDecision decision : decisions) {
			switch (decision) {
				case Allowed allowed -> {
				}
				case Exceeded exceeded -> {
					if (latest == null || exceeded.retryAt().isAfter(latest)) {
						latest = exceeded.retryAt();
					}
				}
			}
		}
		return (latest == null) ? new Allowed() : new Exceeded(latest);
	}

}
