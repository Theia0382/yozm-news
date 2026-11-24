package net.yozmnews.report.domain.input;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.user.TopicSubscriptionAction;

/**
 * 구독 변경 줄 — 기간 안에 사용자가 직접 바꾼 주제 구독 하나 · docs/specs/report.md §5 · report-R17
 *
 * @param names 그 행이 덮던 중분류의 이름 — 체계 순서, 비지 않는다
 */
public record TopicSubscriptionLine(Instant at, TopicSubscriptionAction action, List<String> names) {

	/**
	 * @throws IllegalArgumentException 이름이 없을 때
	 */
	public TopicSubscriptionLine {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(action, "action");
		names = List.copyOf(Objects.requireNonNull(names, "names"));
		if (names.isEmpty()) {
			throw new IllegalArgumentException("구독 변경 줄에 중분류가 없다");
		}
	}

}
