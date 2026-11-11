package net.yozmnews.tag;

import java.util.List;
import java.util.Objects;

/**
 * 태그 체계 원문 확인의 결과 — 아무것도 쓰지 않는다 — docs/specs/tag.md §3 · tag-R16
 */
public sealed interface TagSetReviewResult {

	/**
	 * 활성화할 수 있다.
	 *
	 * @param recentArticles 재평가 기간에 처음 본 기사 수
	 */
	record Ready(TagSetChanges changes, long recentArticles) implements TagSetReviewResult {

		public Ready {
			Objects.requireNonNull(changes, "changes");
		}

	}

	/** 내용이 기준 버전과 같다 */
	record Unchanged() implements TagSetReviewResult {
	}

	/** 원문 문제 · 기준 버전 대조 문제가 있다 — 찾은 순서대로 모두 */
	record Invalid(List<TagSetProblemView> problems) implements TagSetReviewResult {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

	/** 재평가 기간이 0 ~ {@link TagSetAdministration#MAX_REEVALUATION_DAYS} 밖이다 */
	record DaysOutOfRange() implements TagSetReviewResult {
	}

}
