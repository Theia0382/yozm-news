package net.yozmnews.tag;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;

/**
 * 다른 모듈이 기사의 평가를 읽는 조회 — 붙은 태그 · 형식 · 중요도 · docs/specs/tag.md §3.
 * <p>
 * 붙었는지는 조회할 때 그 평가 버전의 지금 태그 기준 · 저장 기준으로 판정한다. 트랜잭션을 열지 않는다.
 */
public interface ArticleEvaluations {

	/** {@link #findAll} 한 번에 묻는 기사 수의 상한 — 정본. 측정 전 목표 */
	int MAX_ARTICLES = 1000;

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

	/**
	 * 기사들의 평가를 읽는다 — 기사마다 {@link #find} 와 같은 평가 · 같은 판정. 다이제스트 후보 선별이 읽는다.
	 * @return 기사 ID → 평가 — 평가하지 않은 기사는 빠진다. 빈 입력이면 빈 맵
	 * @throws IllegalArgumentException 서로 다른 기사가 {@link #MAX_ARTICLES} 개를 넘을 때 — 부르는 쪽이 나눠 부른다
	 */
	Map<ArticleId, ArticleEvaluationView> findAll(Collection<ArticleId> articleIds);

	/**
	 * 첫 평가 시각(기사 평가 행 가운데 가장 이른 평가 시각 — 재평가로 바뀌지 않는다)이 기간 안인 기사 — 기사 ID 순, 한 번씩. 다이제스트 후보
	 * 선별이 그 기간에 새로 평가된 기사를 고른다.
	 */
	List<ArticleId> firstEvaluatedIn(EvaluationPeriod period);

}
