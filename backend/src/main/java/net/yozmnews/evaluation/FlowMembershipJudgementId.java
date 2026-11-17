package net.yozmnews.evaluation;

/**
 * 흐름 소속 판정 원본의 식별자 — {@code flow_membership_judgements.id} · docs/specs/evaluation.md §3 · evaluation-R52.
 * <p>
 * 판정을 반영한 쪽(스토리라인 잇기)이 자기 기록에 남겨 판정과 결과를 잇는다.
 */
public record FlowMembershipJudgementId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public FlowMembershipJudgementId {
		if (value < 1) {
			throw new IllegalArgumentException("판정 원본 ID 는 1 이상이다: " + value);
		}
	}

}
