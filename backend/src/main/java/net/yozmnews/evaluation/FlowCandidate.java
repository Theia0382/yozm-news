package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 후보 흐름 하나 — 흐름 ID 와 서술 기사(부르는 쪽이 고른다) · docs/specs/evaluation.md §3 · evaluation-R51.
 * <p>
 * 흐름 ID 는 뜻을 모르는 값이다 — 부르는 쪽(스토리라인 잇기)이 스토리라인이나 사건을 가리키게 정한다. 서술 기사 수의 상한은
 * {@link EventDescriptor#MAX_ARTICLES} 가 정본이다.
 *
 * @param flowId 후보 흐름 ID
 * @param articleIds 서술 기사 — 이 순서로 그 제목을 질문에 싣는다
 */
public record FlowCandidate(long flowId, List<ArticleId> articleIds) {

	/**
	 * @throws IllegalArgumentException 흐름 ID 가 1 미만이거나, 서술 기사가 없거나 {@value EventDescriptor#MAX_ARTICLES}개를 넘거나
	 * 겹칠 때
	 */
	public FlowCandidate {
		articleIds = MembershipChecks.candidate(flowId, articleIds, "흐름");
	}

}
