package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 스토리라인 소속 판정의 후보 하나 — 후보 ID 와 서술 기사(부르는 쪽이 고른다) · docs/specs/evaluation.md §3 · evaluation-R51.
 * <p>
 * 후보 ID 는 부르는 쪽({@code storyline} 의 잇기)의 스토리라인(붙이기)이나 이웃 사건(짝)을 가리킨다 — 이 모듈은 받아들일 기준을
 * 모른다. 서술 기사 수의 상한은 {@link EventDescriptor#MAX_ARTICLES} 가 정본이다.
 *
 * @param candidateId 후보 ID
 * @param articleIds 서술 기사 — 이 순서로 그 제목을 질문에 싣는다
 */
public record StorylineCandidate(long candidateId, List<ArticleId> articleIds) {

	/**
	 * @throws IllegalArgumentException 후보 ID 가 1 미만이거나, 서술 기사가 없거나 {@value EventDescriptor#MAX_ARTICLES}개를 넘거나
	 * 겹칠 때
	 */
	public StorylineCandidate {
		articleIds = MembershipChecks.candidate(candidateId, articleIds, "후보");
	}

}
