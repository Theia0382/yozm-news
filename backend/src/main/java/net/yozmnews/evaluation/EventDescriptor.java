package net.yozmnews.evaluation;

import java.util.HashSet;
import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 후보 사건 하나 — 사건 ID 와 서술 기사(사건 중심에 가까운 기사, 부르는 쪽이 고른다) · docs/specs/evaluation.md §3 · evaluation-R37.
 *
 * @param eventId 후보 사건 ID
 * @param articleIds 서술 기사 — 이 순서로 그 제목을 질문에 싣는다
 */
public record EventDescriptor(long eventId, List<ArticleId> articleIds) {

	/** 후보 하나의 서술 기사 상한 — 정본. 측정 전 목표 */
	public static final int MAX_ARTICLES = 5;

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 서술 기사가 없거나 {@value #MAX_ARTICLES}개를 넘거나 겹칠 때
	 */
	public EventDescriptor {
		if (eventId < 1) {
			throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + eventId);
		}
		articleIds = List.copyOf(articleIds);
		if (articleIds.isEmpty()) {
			throw new IllegalArgumentException("서술 기사가 없다");
		}
		if (articleIds.size() > MAX_ARTICLES) {
			throw new IllegalArgumentException("서술 기사는 " + MAX_ARTICLES + "개까지다: " + articleIds.size());
		}
		if (new HashSet<>(articleIds).size() != articleIds.size()) {
			throw new IllegalArgumentException("서술 기사가 겹친다");
		}
	}

}
