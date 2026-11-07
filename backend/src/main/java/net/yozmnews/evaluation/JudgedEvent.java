package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 판정 원본에 남은 물은 후보 하나 · docs/specs/evaluation.md §3 · evaluation-R38.
 *
 * @param eventId 판정 당시의 후보 사건 ID — {@code event} 의 사건을 가리키는 값이고 {@code evaluation} 은 그 뜻을 모른다
 * @param describedArticles 질문에 쓴 서술 기사 — 요청 순서
 * @param probability 그 사건에 속할 확률
 */
public record JudgedEvent(long eventId, List<ArticleId> describedArticles, double probability) {

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 서술 기사가 없거나, 확률이 0 이상 1 이하의 유한값이 아닐 때
	 */
	public JudgedEvent {
		if (eventId < 1) {
			throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + eventId);
		}
		Objects.requireNonNull(describedArticles, "describedArticles");
		describedArticles = List.copyOf(describedArticles);
		if (describedArticles.isEmpty()) {
			throw new IllegalArgumentException("서술 기사가 없다");
		}
		if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
			throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
		}
	}

}
