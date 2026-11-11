package net.yozmnews.evaluation.domain.tagging;

import java.util.Objects;

/**
 * 다시 읽을 태그 판정 원본 — docs/specs/evaluation.md §4 · evaluation-R42.
 *
 * @param evaluator 원본을 남긴 평가기 이름
 * @param rawResponse 원본 응답 — JSON 텍스트(공백 · 키 순서는 원문과 다를 수 있다)
 */
public record StoredTagJudgement(String evaluator, String rawResponse) {

	public StoredTagJudgement {
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(rawResponse, "rawResponse");
	}

}
