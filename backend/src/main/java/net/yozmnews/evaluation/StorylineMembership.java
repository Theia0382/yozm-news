package net.yozmnews.evaluation;

/**
 * 스토리라인 소속 판정 — 판정할 기사가 후보마다 그 후보와 같은 사안의 전개인지 평가기로 판정한다 · docs/specs/evaluation.md §3 ·
 * evaluation-R51 · evaluation-R52.
 * <p>
 * 작업 역할의 작업에서 DB 트랜잭션 밖에서 부른다 — 트랜잭션 안이거나 평가기가 없는 프로세스면 {@link IllegalStateException} 이다. 확률을
 * 받아들일지는 부르는 쪽이 정한다.
 */
public interface StorylineMembership {

	StorylineMembershipJudgement judge(StorylineMembershipQuery query);

}
