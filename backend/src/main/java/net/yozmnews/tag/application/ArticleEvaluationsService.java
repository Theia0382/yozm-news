package net.yozmnews.tag.application;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;
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

	@Override
	public Map<ArticleId, ArticleEvaluationView> findAll(Collection<ArticleId> articleIds) {
		Set<ArticleId> distinct = new LinkedHashSet<>(articleIds);
		if (distinct.size() > MAX_ARTICLES) {
			throw new IllegalArgumentException("한 번에 묻는 기사가 " + MAX_ARTICLES + " 개를 넘는다: " + distinct.size());
		}
		// 버전 내용과 그 기준은 버전마다 한 번만 읽는다 — 평가가 많아도 질의는 평가 질의 하나 + 버전 수 × 2 다
		Map<TagSetId, TagSetView> versions = new HashMap<>();
		Map<TagSetId, TagSetThresholds> thresholds = new HashMap<>();
		Map<ArticleId, ArticleEvaluationView> views = new LinkedHashMap<>();
		for (StoredEvaluation stored : this.results.findLatestAll(distinct)) {
			views.put(stored.articleId(),
					view(stored, versions.computeIfAbsent(stored.tagSetId(), this.tagSets::of),
							thresholds.computeIfAbsent(stored.tagSetId(), this.thresholds::current)));
		}
		return Collections.unmodifiableMap(views);
	}

	@Override
	public List<ArticleId> firstEvaluatedIn(EvaluationPeriod period) {
		return this.results.firstEvaluatedIn(period.from(), period.to());
	}

	private ArticleEvaluationView view(StoredEvaluation stored) {
		return view(stored, this.tagSets.of(stored.tagSetId()), this.thresholds.current(stored.tagSetId()));
	}

	private static ArticleEvaluationView view(StoredEvaluation stored, TagSetView version,
			TagSetThresholds thresholds) {
		Judged judged = TagJudgement.judge(stored.tags(), version, thresholds);
		return new ArticleEvaluationView(stored.articleId(), stored.tagSetId(), judged.status(), judged.topics(),
				judged.topicCategories(), judged.regions(), stored.formatKey(), stored.importance());
	}

}
