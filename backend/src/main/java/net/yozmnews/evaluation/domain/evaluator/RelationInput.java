package net.yozmnews.evaluation.domain.evaluator;

import java.util.Objects;

/**
 * 관계 판정의 입력 — 두 흐름의 서술과 문구 · docs/specs/evaluation.md §4 · evaluation-R48.
 *
 * @param first 요청의 A
 * @param second 요청의 B
 * @param wording 문구
 */
public record RelationInput(FlowText first, FlowText second, RelationWording wording) {

	public RelationInput {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(second, "second");
		Objects.requireNonNull(wording, "wording");
	}

}
