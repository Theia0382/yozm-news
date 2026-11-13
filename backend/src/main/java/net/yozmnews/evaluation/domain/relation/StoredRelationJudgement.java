package net.yozmnews.evaluation.domain.relation;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.RelationJudgementId;

/**
 * 읽은 관계 판정 원본 하나 — 운영자 조회가 다시 읽는다 · docs/specs/evaluation.md §4 · evaluation-R50.
 * <p>
 * 서술은 싣지 않는다 — 조회가 쓰지 않는다.
 *
 * @param firstFlowId 요청의 first 흐름
 * @param secondFlowId 요청의 second 흐름
 * @param rawResponse 평가기의 원본 응답 — {@code jsonb} 에서 꺼낸 JSON 텍스트(공백 · 키 순서는 원문과 다르다)
 */
public record StoredRelationJudgement(RelationJudgementId id, long firstFlowId, long secondFlowId, String evaluator,
		String modelVersion, String wordingVersion, int inputTokens, String rawResponse, Instant judgedAt) {

	public StoredRelationJudgement {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(wordingVersion, "wordingVersion");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(judgedAt, "judgedAt");
	}

}
