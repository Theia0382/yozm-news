package net.yozmnews.tag.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.StoredEvaluation;
import net.yozmnews.tag.domain.result.TagJudgement;
import net.yozmnews.tag.domain.result.TagJudgement.Judged;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link ArticleEvaluations} 의 구현 — docs/specs/tag.md §4 · tag-R15.
 * <p>
 * 평가 행의 버전과 그 버전의 지금 태그 기준 · 저장 기준으로 {@link TagJudgement} 가 판정한다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ArticleEvaluationsService implements ArticleEvaluations {

	private final EvaluationResultRepository results;

	private final TagSetViews tagSets;

	private final ThresholdRepository thresholds;

	ArticleEvaluationsService(EvaluationResultRepository results, TagSetViews tagSets, ThresholdRepository thresholds) {
		this.results = results;
		this.tagSets = tagSets;
		this.thresholds = thresholds;
	}

	@Override
	public Optional<ArticleEvaluationView> find(ArticleId articleId) {
		return this.results.findLatest(articleId).map(this::view);
	}

	@Override
	public Map<ArticleId, Double> importances(Collection<ArticleId> articleIds) {
		return this.results.findLatestImportances(articleIds);
	}

	private ArticleEvaluationView view(StoredEvaluation stored) {
		Judged judged = TagJudgement.judge(stored.tags(), this.tagSets.of(stored.tagSetId()),
				this.thresholds.current(stored.tagSetId()));
		return new ArticleEvaluationView(stored.articleId(), stored.tagSetId(), judged.status(), judged.topics(),
				judged.topicCategories(), judged.regions(), stored.formatKey(), stored.importance());
	}

}
