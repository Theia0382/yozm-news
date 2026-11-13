package net.yozmnews.evaluation.domain.relation;

import net.yozmnews.evaluation.RelationJudgementId;

/**
 * 관계 판정 원본 저장소 — 넣기 · docs/specs/evaluation.md §4
 */
public interface RelationJudgementRepository {

	/**
	 * 판정 원본 한 행을 넣는다 — 같은 쌍의 판정이 여러 번이면 행도 여러 개다.
	 * @return 넣은 행의 ID
	 */
	RelationJudgementId insert(RelationRecord record);

}
