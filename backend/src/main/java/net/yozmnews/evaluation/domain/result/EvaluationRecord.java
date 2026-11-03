package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import org.jspecify.annotations.Nullable;

/**
 * 저장할 평가 한 건 — 결과 해석(저장 기준 거르기 · 형식 대표값 · 중요도 기대값)의 정본 · docs/specs/evaluation.md §5.
 *
 * @param formatKey 대표 형식 — 확률이 가장 큰 선택지, 같으면 체계 순서가 앞선 것
 * @param formatDistribution 형식 선택지 → 확률 — 평가기가 준 분포 그대로
 * @param importance 중요도 기대값 — 확률 합이 0 이면 null
 * @param tags 저장 기준 이상인 주제 · 지역 태그 — 주제 다음 지역, 체계 순서
 */
public record EvaluationRecord(ArticleId articleId, TagSetId tagSetId, String evaluator, String modelVersion,
		String formatKey, double formatProbability, Map<String, Double> formatDistribution,
		@Nullable Double importance, Map<Integer, Double> importanceDistribution, List<StoredTag> tags,
		String rawResponse, int inputTokens, Instant evaluatedAt) {

	public EvaluationRecord {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(tagSetId, "tagSetId");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(formatKey, "formatKey");
		formatDistribution = Map.copyOf(formatDistribution);
		importanceDistribution = Map.copyOf(importanceDistribution);
		tags = List.copyOf(tags);
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
	}

	/**
	 * 평가 결과를 저장할 모양으로 옮긴다. 결과는 {@link ResultCheck} 로 체계와 맞는지 본 것이어야 한다.
	 * @param thresholds 평가 때의 태그 기준 · 저장 기준 — 체계와 같은 버전
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다를 때
	 */
	public static EvaluationRecord of(ArticleId articleId, Evaluation evaluation, TagSetView tagSet,
			TagSetThresholds thresholds, Instant evaluatedAt) {
		VersionMatch.require(thresholds, tagSet);
		List<StoredTag> tags = new ArrayList<>();
		List<AskedTag> subtopics = tagSet.topics().stream().flatMap(category -> category.subtopics().stream()).toList();
		stored(tags, thresholds.topic(), subtopics, evaluation.topics());
		stored(tags, thresholds.region(), tagSet.regions(), evaluation.regions());
		FormatOption chosen = null;
		double chosenProbability = 0;
		for (FormatOption option : tagSet.format().options()) {
			double probability = evaluation.formats().get(option.key());
			if (chosen == null || probability > chosenProbability) {
				chosen = option;
				chosenProbability = probability;
			}
		}
		if (chosen == null) {
			throw new IllegalArgumentException("형식 선택지가 없다");
		}
		return new EvaluationRecord(articleId, tagSet.id(), evaluation.evaluator(), evaluation.modelVersion(),
				chosen.key(), chosenProbability, evaluation.formats(), ImportanceScore.expected(evaluation.importance()),
				evaluation.importance(), tags, evaluation.rawResponse(), evaluation.inputTokens(), evaluatedAt);
	}

	private static void stored(List<StoredTag> into, TagThresholds thresholds, List<AskedTag> asked,
			Map<String, Double> probabilities) {
		MultiTagKind kind = thresholds.kind();
		for (AskedTag tag : asked) {
			double probability = probabilities.get(tag.key());
			if (Probabilities.atLeast(probability, thresholds.storeThreshold())) {
				into.add(new StoredTag(kind, tag.key(), probability));
			}
		}
	}

}
