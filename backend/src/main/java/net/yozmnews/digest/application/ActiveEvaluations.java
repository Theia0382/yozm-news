package net.yozmnews.digest.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.selection.ActiveEvaluation;
import net.yozmnews.digest.domain.selection.ActiveKeys;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetId;
import org.springframework.stereotype.Service;

/**
 * 기사들의 평가를 읽어 활성 키로 푼다 — 후보 선별과 관심도 재료가 함께 쓰는 정본. docs/specs/digest.md §4 · digest-R16
 * <p>
 * 평가는 {@link ArticleEvaluations#MAX_ARTICLES} 씩 나눠 읽고, 평가 버전마다 계보 표를 한 번 읽는다. 표의 활성 버전이 부르는 쪽이 맞출 버전과
 * 다르면(조회 사이의 태그 체계 활성 전환) 빈 값이다 — 부르는 쪽이 처음부터 다시 한다.
 */
@Service
class ActiveEvaluations {

	/**
	 * 활성 버전이 어긋날 때(조회 사이의 태그 체계 활성 전환) 처음부터 다시 하는 횟수를 포함한 전체 시도 수 — 평가를 활성 키로 풀어 읽는 작업이 함께
	 * 쓰는 정본
	 */
	static final int ATTEMPTS = 2;

	private final ArticleEvaluations evaluations;

	private final TagKeyLineage lineage;

	ActiveEvaluations(ArticleEvaluations evaluations, TagKeyLineage lineage) {
		this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
		this.lineage = Objects.requireNonNull(lineage, "lineage");
	}

	/**
	 * @param ids 기사 — 평가하지 않았거나 그 사이 지운 기사는 결과에 없다
	 * @param active 맞출 활성 버전
	 * @return 기사 → 활성 키로 푼 평가(읽은 순서). 계보 표의 활성 버전이 {@code active} 와 다르면 빈 값
	 */
	Optional<Map<ArticleId, ActiveEvaluation>> read(Collection<ArticleId> ids, TagSetId active) {
		Objects.requireNonNull(ids, "ids");
		Objects.requireNonNull(active, "active");
		List<ArticleId> distinct = ids.stream().distinct().toList();
		Map<ArticleId, ArticleEvaluationView> views = new LinkedHashMap<>();
		for (int i = 0; i < distinct.size(); i += ArticleEvaluations.MAX_ARTICLES) {
			int end = Math.min(distinct.size(), i + ArticleEvaluations.MAX_ARTICLES);
			views.putAll(this.evaluations.findAll(distinct.subList(i, end)));
		}
		Map<TagSetId, TagKeyMapping> mappings = new HashMap<>();
		Map<ArticleId, ActiveEvaluation> resolved = new LinkedHashMap<>();
		for (ArticleEvaluationView view : views.values()) {
			TagKeyMapping mapping = mappings.computeIfAbsent(view.tagSetId(), this.lineage::toActive);
			if (!mapping.to().equals(active)) {
				return Optional.empty();
			}
			resolved.put(view.articleId(), ActiveKeys.resolve(view, mapping));
		}
		return Optional.of(resolved);
	}

}
