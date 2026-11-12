package net.yozmnews.tag;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;

/**
 * 다른 모듈이 기사의 평가를 읽는 조회 — 붙은 태그 · 형식 · 중요도 · docs/specs/tag.md §3.
 * <p>
 * 붙었는지는 조회할 때 그 평가 버전의 지금 태그 기준 · 저장 기준으로 판정한다. 트랜잭션을 열지 않는다.
 */
public interface ArticleEvaluations {

	/**
	 * 기사의 평가를 읽는다 — 평가가 여러 버전이면 버전 번호가 가장 큰 것.
	 * @return 평가하지 않은 기사면 빈 값
	 */
	Optional<ArticleEvaluationView> find(ArticleId articleId);

	/**
	 * 기사들의 중요도 기대값을 읽는다 — 기사마다 버전 번호가 가장 큰 평가의 값({@link #find} 와 같은 평가). 사건 묶기가 기간 집계의
	 * 중요도 평균을 센다(event-R15).
	 * @return 기사 ID → 중요도 기대값(1 ~ 5) — 평가하지 않았거나 그 평가의 중요도가 없으면 빠진다(이전 버전의 값을 쓰지 않는다). 빈
	 *     입력이면 빈 맵
	 */
	Map<ArticleId, Double> importances(Collection<ArticleId> articleIds);

}
