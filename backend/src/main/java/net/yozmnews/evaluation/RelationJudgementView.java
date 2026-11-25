package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 관계 판정 원본 하나 — 운영자 조회의 값 · docs/specs/evaluation.md §3 · evaluation-R50.
 *
 * @param firstStorylineId 그 판정 요청의 first 스토리라인 — 확률의 방향은 이 둘을 기준으로 한다. 판정 뒤 합쳐진 스토리라인일 수 있다
 * @param secondStorylineId 그 판정 요청의 second 스토리라인
 * @param inputTokens 판정 요청의 입력 토큰
 * @param reading 원본 응답을 다시 읽은 결과
 */
public record RelationJudgementView(RelationJudgementId id, long firstStorylineId, long secondStorylineId, String evaluator,
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
