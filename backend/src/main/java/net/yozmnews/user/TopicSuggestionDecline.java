package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

/**
 * 구독 제안 거절 하나 — docs/specs/user.md §3 · user-R65
 *
 * @param subtopic 활성 버전의 주제 중분류 전체 키
 * @param level 거절 때 관심 x(−1 ~ 1)
 * @param declinedAt 거절한 시각
 */
public record TopicSuggestionDecline(String subtopic, double level, Instant declinedAt) {

	public TopicSuggestionDecline {
		Objects.requireNonNull(subtopic, "subtopic");
		Objects.requireNonNull(declinedAt, "declinedAt");
	}

}
