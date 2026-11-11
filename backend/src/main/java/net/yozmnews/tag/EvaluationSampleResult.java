package net.yozmnews.tag;

import java.util.Objects;

/**
 * 표본의 결과 — docs/specs/tag.md §3 · tag-R34.
 */
public sealed interface EvaluationSampleResult {

	/** 골랐다 */
	record Sampled(EvaluationSampleView sample) implements EvaluationSampleResult {

		public Sampled {
			Objects.requireNonNull(sample, "sample");
		}

	}

	/** 없는 버전이다 */
	record UnknownVersion() implements EvaluationSampleResult {
	}

	/** 표본 크기가 1 ~ {@link EvaluationReview#MAX_SAMPLE_SIZE} 밖이다 */
	record SizeOutOfRange() implements EvaluationSampleResult {
	}

	/** 씨앗이 0 ~ {@link EvaluationReview#MAX_SEED} 밖이다 */
	record SeedOutOfRange() implements EvaluationSampleResult {
	}

}
