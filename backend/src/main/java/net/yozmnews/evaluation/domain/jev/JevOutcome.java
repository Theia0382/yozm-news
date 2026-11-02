package net.yozmnews.evaluation.domain.jev;

import java.util.Objects;

import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;

/**
 * 200 이 아닌 Jev 응답의 갈래 — docs/specs/evaluation.md §4 Jev 대응표
 */
public sealed interface JevOutcome permits JevOutcome.Failed, JevOutcome.ContextExceeded {

	/** 그 갈래의 실패다 */
	record Failed(EvaluationFailure.Kind kind) implements JevOutcome {

		public Failed {
			Objects.requireNonNull(kind, "kind");
		}

	}

	/** 문맥을 넘었다 — 원인이 기사인지 체계인지 확인 요청으로 가른다 */
	record ContextExceeded() implements JevOutcome {

	}

}
