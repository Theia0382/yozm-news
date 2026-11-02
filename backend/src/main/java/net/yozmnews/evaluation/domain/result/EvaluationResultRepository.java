package net.yozmnews.evaluation.domain.result;

import java.util.Collection;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagSetId;

/**
 * 평가 결과 저장소 — 넣기 한 번 · 조회 · 지운 기사 따르기 · docs/specs/evaluation.md §4
 */
public interface EvaluationResultRepository {

	/** 그 기사의 그 버전 평가가 있는가 */
	boolean exists(ArticleId articleId, TagSetId tagSetId);

	/** 평가 행과 태그 행을 넣는다 — 기사 · 버전마다 하나다(고유 제약) */
	void insert(EvaluationRecord record);

	/** 기사의 평가 가운데 버전 번호가 가장 큰 것과 그 태그 행 */
	Optional<StoredEvaluation> findLatest(ArticleId articleId);

	/** 기사들의 평가 · 태그 행을 지운다 — 지운 기사를 따른다 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
