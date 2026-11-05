package net.yozmnews.evaluation;

/**
 * 사건 소속 판정 — 기사 하나가 후보 사건마다 그 사건을 다루는지 평가기로 판정한다 · docs/specs/evaluation.md §3 · evaluation-R37 ·
 * evaluation-R38.
 * <p>
 * 판정은 외부 호출이라 작업 역할의 작업에서, DB 트랜잭션 밖에서 부른다. 판정에 성공하면 판정 원본을 남긴다. 확률을 받아들일지(기준 ·
 * 최댓값)는 부르는 쪽이 정한다.
 */
public interface EventMembership {

	/**
	 * 기사 하나가 후보 사건마다 그 사건을 다루는지 판정한다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀거나, 평가기가 없는 프로세스에서 불렀을 때 — 부르는 쪽 결함이다
	 */
	MembershipJudgement judge(MembershipQuery query);

}
