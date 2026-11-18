package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 관심도가 보는 노출 기사 하나 — docs/specs/digest.md §5
 *
 * @param subtopics 기사의 주제 중분류 — 활성 키
 * @param exposedAt 그 사용자의 다이제스트가 이 기사를 인용한 가장 늦은 시각 — 노출과 그 반응의 감쇠를 함께 정한다
 * @param reaction 그 기사에 남긴 마지막 값 — 없으면 {@link FeedbackValue#NONE}
 */
public record ExposedArticle(Set<String> subtopics, Instant exposedAt, FeedbackValue reaction) {

	public ExposedArticle {
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		Objects.requireNonNull(exposedAt, "exposedAt");
		Objects.requireNonNull(reaction, "reaction");
	}

}
