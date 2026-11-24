package net.yozmnews.tag;

import java.util.List;
import java.util.Objects;

/**
 * 평가 목록의 결과 — docs/specs/tag.md §3 · tag-R33.
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

	/** 거를 태그 가운데 그 버전의 그 태그 종류에 없는 것이 있다 — 그 태그들(고른 순서). 아무것도 읽지 않았다 */
	record UnknownTags(List<TagRef> tags) implements EvaluationListResult {

		/**
		 * @throws IllegalArgumentException 태그가 없을 때
		 */
		public UnknownTags {
			tags = List.copyOf(tags);
			if (tags.isEmpty()) {
				throw new IllegalArgumentException("없는 태그가 하나는 있어야 한다");
			}
		}

	}

	/** 거를 태그가 {@link EvaluationReview#MAX_FILTER_TAGS} 개를 넘는다 */
	record TooManyTags() implements EvaluationListResult {
	}

	/** 형식 태그를 둘 이상 골랐다 — 대표 형식은 하나라 맞는 평가가 없다 */
	record MultipleFormats() implements EvaluationListResult {
	}

	/** 한 번에 받을 수가 1 ~ {@link EvaluationReview#MAX_PAGE_SIZE} 밖이다 */
	record LimitOutOfRange() implements EvaluationListResult {
	}

}
