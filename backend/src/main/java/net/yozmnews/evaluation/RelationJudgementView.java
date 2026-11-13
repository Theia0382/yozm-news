package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 관계 판정 원본 하나 — 운영자 조회의 값 · docs/specs/evaluation.md §3 · evaluation-R50.
 *
 * @param firstFlowId 그 판정 요청의 first 흐름 — 확률의 방향은 이 둘을 기준으로 한다. 판정 뒤 합쳐진 흐름일 수 있다
 * @param secondFlowId 그 판정 요청의 second 흐름
 * @param inputTokens 판정 요청의 입력 토큰
 * @param reading 원본 응답을 다시 읽은 결과
 */
public record RelationJudgementView(RelationJudgementId id, long firstFlowId, long secondFlowId, String evaluator,
		String modelVersion, String wordingVersion, int inputTokens, Instant judgedAt, RelationReading reading) {

	public RelationJudgementView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(wordingVersion, "wordingVersion");
		Objects.requireNonNull(judgedAt, "judgedAt");
		Objects.requireNonNull(reading, "reading");
	}

}
