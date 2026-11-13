package net.yozmnews.storyline;

import java.util.List;

/**
 * 운영자 조회 — 운영자 화면이 스토리라인의 판정 근거를 읽는다 · docs/specs/storyline.md §3 · storyline-R34.
 * <p>
 * 사용자 쪽 공개 조회({@link StorylineDirectory})가 싣지 않는 소속 근거 · 관계의 확률 · 물은 기록 · 지금 기준을 낸다. ID 로 찾는 조회는
 * 남는 쪽을 따르고, 없는 스토리라인이면 빈 목록이다. 사용자 정보를 받지 않는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통). 각 질의는
 * 그 순간의 값이다 — 모듈 잠금을 잡지 않는다.
 */
public interface StorylineReview {

	/** 스토리라인과 그 모든 하위의 직접 소속 전부와 저마다의 판정 근거 — 스토리라인 ID · 사건 ID 순 */
	List<StorylineMembership> memberships(StorylineId id);

	/** {@link StorylineDirectory#relationsOf} 와 같은 관계 · 같은 순서에 맺은 확률 · 판정 원본 ID · 그때의 관계 기준 */
	List<StorylineRelationDetail> relations(StorylineId id);

	/** 이 스토리라인이 든 물은 기록 — 상대 ID 순, 쌍마다 마지막 한 번 */
	List<StorylineRelationAsked> asked(StorylineId id);

	/** 지금 잇기 기준 — 설정 */
	StorylineLinkingCriteria linkingCriteria();

	/** 지금 관계 기준 — 설정 */
	StorylineRelationCriteria relationCriteria();

}
