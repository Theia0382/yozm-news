package net.yozmnews.evaluation;

/**
 * 관계 판정 — 두 흐름(사건들의 묶음 둘)이 어떤 관계인지 평가기로 판정한다 · docs/specs/evaluation.md §3 · evaluation-R48 ·
 * evaluation-R49.
 * <p>
 * 판정은 외부 호출이라 작업 역할의 작업에서, DB 트랜잭션 밖에서 부른다. 판정에 성공하면 판정 원본을 남긴다. 답을 받아들일지(확률 기준 · 한
 * 사안 기준 · 구조 제약)는 부르는 쪽이 정한다.
 */
public interface FlowRelation {

	/**
	 * 두 흐름의 관계를 판정한다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀거나, 평가기가 없는 프로세스에서 불렀을 때 — 부르는 쪽 결함이다. 평가기의 결과가
	 * 답 다섯과 어긋날 때도 던진다(판정 원본을 남기지 않는다)
	 */
	RelationJudgement judge(RelationQuery query);

}
