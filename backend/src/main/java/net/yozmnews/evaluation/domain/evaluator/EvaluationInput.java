package net.yozmnews.evaluation.domain.evaluator;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 평가할 기사 하나 — docs/specs/evaluation.md §4.
 *
 * @param summary RSS 요약문 — 없으면 null. 길이 상한은 수집 쪽이 정한다
 * @param feeds 기사가 실린 피드의 맥락 — 넘긴 순서 그대로, 비어 있을 수 있다
 */
public record EvaluationInput(String title, @Nullable String summary, List<FeedContext> feeds) {

	/**
	 * @throws IllegalArgumentException 제목이 공백뿐이거나 요약이 공백뿐일 때
	 */
	public EvaluationInput {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("제목이 비었다");
		}
		if (summary != null && summary.isBlank()) {
			throw new IllegalArgumentException("요약이 공백뿐이다 — 없으면 null 이다");
		}
		feeds = List.copyOf(feeds);
	}

	/**
	 * 저장된 요약을 평가 입력의 요약으로 — 공백뿐인 요약은 없는 요약이다(evaluation-R40). 평가 회차 · 사건 소속 판정이 함께 쓰는 정본
	 */
	public static @Nullable String presentSummary(@Nullable String summary) {
		return (summary == null || summary.isBlank()) ? null : summary;
	}

}
