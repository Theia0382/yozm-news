package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 사건 소속 판정 요청 — 판정할 기사와 후보 사건들 · docs/specs/evaluation.md §3 · evaluation-R37.
 *
 * @param articleId 판정할 기사
 * @param candidates 후보 사건 — 이 순서로 묻는다
 */
public record MembershipQuery(ArticleId articleId, List<EventDescriptor> candidates) {

	/** 한 요청의 후보 상한 — 정본(스토리라인 소속 판정도 쓴다). 측정 전 목표 */
	public static final int MAX_CANDIDATES = 10;

	/**
	 * @throws IllegalArgumentException 후보가 없거나 {@value #MAX_CANDIDATES}개를 넘거나, 사건 ID 가 겹치거나, 판정할 기사가 어느
	 * 후보의 서술 기사에 들어 있을 때
	 */
	public MembershipQuery {
		candidates = List.copyOf(candidates);
		MembershipChecks.query(articleId, candidates, EventDescriptor::eventId, EventDescriptor::articleIds);
	}

}
