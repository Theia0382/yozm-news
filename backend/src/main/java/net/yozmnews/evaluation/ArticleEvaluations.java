package net.yozmnews.evaluation;

import java.util.Optional;

import net.yozmnews.article.ArticleId;

/**
 * 다른 모듈이 기사의 평가를 읽는 조회 — 붙은 태그 · 형식 · 중요도 · docs/specs/evaluation.md §3.
 * <p>
 * 붙었는지는 조회할 때 그 평가 버전의 지금 태그 기준 · 저장 기준으로 판정한다. 트랜잭션을 열지 않는다.
 */
public interface ArticleEvaluations {

	/**
	 * 기사의 평가를 읽는다 — 평가가 여러 버전이면 버전 번호가 가장 큰 것.
	 * @return 평가하지 않은 기사면 빈 값
	 */
	Optional<ArticleEvaluationView> find(ArticleId articleId);

}
