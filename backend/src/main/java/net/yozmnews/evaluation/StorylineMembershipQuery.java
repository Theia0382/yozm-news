package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 스토리라인 소속 판정 요청 — 판정할 기사와 후보들 · docs/specs/evaluation.md §3 · evaluation-R51.
 * <p>
 * 후보 수의 상한은 {@link MembershipQuery#MAX_CANDIDATES} 가 정본이다.
 *
 * @param articleId 판정할 기사
 * @param candidates 후보 — 이 순서로 묻는다
 */
public record StorylineMembershipQuery(ArticleId articleId, List<StorylineCandidate> candidates) {

	/**
	 * @throws IllegalArgumentException 후보가 없거나 {@value MembershipQuery#MAX_CANDIDATES}개를 넘거나, 후보 ID 가 겹치거나, 판정할
	 * 기사가 어느 후보의 서술 기사에 들어 있을 때
	 */
	public StorylineMembershipQuery {
		candidates = List.copyOf(candidates);
		MembershipChecks.query(articleId, candidates, StorylineCandidate::candidateId, StorylineCandidate::articleIds);
	}

}
