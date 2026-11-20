package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 관심도가 보는 노출 사건 하나 — docs/specs/digest.md §5
 *
 * @param subtopics 사건의 주제 중분류 — 활성 키
 * @param exposedAt 그 사용자의 다이제스트가 이 사건을 보인 가장 늦은 시각 — 노출과 그 반응의 감쇠를 함께 정한다
 * @param share 그 가장 늦은 노출의 몫 — 노출과 그 반응에 함께 곱한다. 0 보다 크고 1 이하
 * @param reaction 그 사건에 남은 마지막 값 — 없으면 {@link FeedbackValue#NONE}
 */
public record ExposedEvent(Set<String> subtopics, Instant exposedAt, double share, FeedbackValue reaction) {

	public ExposedEvent {
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		Objects.requireNonNull(exposedAt, "exposedAt");
		Objects.requireNonNull(reaction, "reaction");
		Exposure.requireShare(share);
	}

}
