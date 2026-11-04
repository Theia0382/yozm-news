package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 빠진 태그 채움 확인의 결과 — docs/specs/evaluation.md §3
 */
public sealed interface TagBackfillReviewResult {

	/**
	 * 채울 수 있다.
	 *
	 * @param from 덮을 평가 시각의 시작 — 포함
	 * @param to 덮을 평가 시각의 끝 — 제외, 확인한 시각
	 * @param evaluations 그 범위의 평가 수
	 * @param versions 평가가 있는 버전마다 평가 수 — 버전 번호 순
	 */
	record Ready(Instant from, Instant to, long evaluations, List<TagBackfillVersionCount> versions)
			implements TagBackfillReviewResult {

		public Ready {
			Objects.requireNonNull(from, "from");
			Objects.requireNonNull(to, "to");
			versions = List.copyOf(versions);
		}

	}

	/** 일수가 1 ~ {@link TagBackfillAdministration#MAX_DAYS} 밖이라 아무것도 하지 않았다 */
	record DaysOutOfRange() implements TagBackfillReviewResult {
	}

}
