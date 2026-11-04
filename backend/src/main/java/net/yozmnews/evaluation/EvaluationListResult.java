package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 평가 목록의 결과 — docs/specs/evaluation.md §3 · evaluation-R33.
 */
public sealed interface EvaluationListResult {

	/** 읽었다 */
	record Listed(EvaluationListing listing) implements EvaluationListResult {

		public Listed {
			Objects.requireNonNull(listing, "listing");
		}

	}

	/** 없는 버전이다 */
	record UnknownVersion() implements EvaluationListResult {
	}

	/** 거를 태그가 그 버전의 그 태그 종류에 없다 — 아무것도 읽지 않았다 */
	record UnknownTag(TagRef tag) implements EvaluationListResult {

		public UnknownTag {
			Objects.requireNonNull(tag, "tag");
		}

	}

	/** 한 번에 받을 수가 1 ~ {@link EvaluationReview#MAX_PAGE_SIZE} 밖이다 */
	record LimitOutOfRange() implements EvaluationListResult {
	}

}
