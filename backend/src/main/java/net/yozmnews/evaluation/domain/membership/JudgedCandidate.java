package net.yozmnews.evaluation.domain.membership;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 판정 원본에 남는 후보 사건 하나 — 사건 ID, 질문에 쓴 서술 기사, 확률 · docs/specs/evaluation.md §4.
 *
 * @param articleIds 질문에 제목을 쓴 서술 기사 — 요청 순서
 * @param probability 그 사건에 속할 확률
 */
public record JudgedCandidate(long eventId, List<ArticleId> articleIds, double probability) {

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 서술 기사가 없거나, 확률이 0 이상 1 이하의 유한값이 아닐 때
	 */
	public JudgedCandidate {
		if (eventId < 1) {
			throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + eventId);
		}
		Objects.requireNonNull(articleIds, "articleIds");
		articleIds = List.copyOf(articleIds);
		if (articleIds.isEmpty()) {
			throw new IllegalArgumentException("서술 기사가 없다");
		}
		if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
			throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
		}
	}

}
