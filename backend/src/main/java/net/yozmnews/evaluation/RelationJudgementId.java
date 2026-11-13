package net.yozmnews.evaluation;

/**
 * 관계 판정 원본의 식별자 — {@code flow_relation_judgements.id} · docs/specs/evaluation.md §3 · evaluation-R49.
 * <p>
 * 판정을 반영한 쪽이 자기 기록에 남겨 판정의 쓰임과 결과를 잇는다.
 */
public record RelationJudgementId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public RelationJudgementId {
		if (value < 1) {
			throw new IllegalArgumentException("판정 원본 ID 는 1 이상이다: " + value);
		}
	}

}
