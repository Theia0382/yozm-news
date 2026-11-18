package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;

/**
 * 거절한 구독 제안 하나 — {@link TopicSuggestionRule} 의 입력. docs/specs/digest.md §5 · digest-R41
 *
 * @param level 거절 때 관심 x
 * @param declinedAt 거절한 시각
 */
public record DeclinedTopic(double level, Instant declinedAt) {

	public DeclinedTopic {
		Objects.requireNonNull(declinedAt, "declinedAt");
	}

}
