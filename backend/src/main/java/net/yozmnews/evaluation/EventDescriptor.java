package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 후보 사건 하나 — 사건 ID 와 서술 기사(사건 중심에 가까운 기사, 부르는 쪽이 고른다) · docs/specs/evaluation.md §3 · evaluation-R37.
 *
 * @param eventId 후보 사건 ID
 * @param articleIds 서술 기사 — 이 순서로 그 제목을 질문에 싣는다
 */
public record EventDescriptor(long eventId, List<ArticleId> articleIds) {

	/** 후보 하나의 서술 기사 상한 — 정본(흐름 소속 판정도 쓴다). 측정 전 목표 */
	public static final int MAX_ARTICLES = 5;

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 서술 기사가 없거나 {@value #MAX_ARTICLES}개를 넘거나 겹칠 때
	 */
	public EventDescriptor {
		articleIds = MembershipChecks.candidate(eventId, articleIds, "사건");
	}

}
