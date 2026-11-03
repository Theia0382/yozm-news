package net.yozmnews.evaluation.application;

import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.ArticleEvaluationView;
import net.yozmnews.evaluation.ArticleEvaluations;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.TagJudgement;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link ArticleEvaluations} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R15.
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

	private ArticleEvaluationView view(StoredEvaluation stored) {
		Judged judged = TagJudgement.judge(stored.tags(), this.tagSets.of(stored.tagSetId()),
				this.thresholds.current(stored.tagSetId()));
		return new ArticleEvaluationView(stored.articleId(), stored.tagSetId(), judged.status(), judged.topics(),
				judged.topicCategories(), judged.regions(), stored.formatKey(), stored.importance());
	}

}
