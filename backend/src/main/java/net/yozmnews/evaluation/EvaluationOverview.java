package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 평가 현황 — 버전 하나의 평가 가운데 평가 시각이 집계 기간 안인 것의 품질 지표 · docs/specs/evaluation.md §3.
 * <p>
 * 판정은 조회 시점에 그 버전의 지금 태그 기준 · 저장 기준으로 정한다. 태그 · 형식 목록은 그 버전의 체계 순서이고 평가가 0 인 태그도 싣는다.
 *
 * @param from 집계 기간의 시작(포함)
 * @param to 집계 기간의 끝(제외)
 * @param evaluations 평가 수 — 비율의 분모
 * @param topics 주제 중분류마다
 * @param topicCategories 주제 대분류마다 — 붙은 중분류의 대분류
 * @param regions 지역마다
 * @param formats 형식마다 — 대표 형식
 * @param topicsPerArticle 판정이 붙인 주제 수의 칸 — 0 은 미분류
 * @param regionsPerArticle 판정이 붙인 지역 수의 칸
 * @param topicProbabilities 저장된 주제 태그 행의 확률
 * @param regionProbabilities 저장된 지역 태그 행의 확률
 * @param formatProbabilities 대표 형식의 확률
 * @param importance 중요도 기대값의 칸
 * @param importanceMissing 중요도 기대값이 없는 평가 수
 * @param inputTokens 평가 요청의 입력 토큰
 */
public record EvaluationOverview(TagSetId version, Instant from, Instant to, long evaluations,
		TopicStatusCounts topicStatus, List<TagShare> topics, List<TagShare> topicCategories, List<TagShare> regions,
		List<TagShare> formats, List<CountBucket> topicsPerArticle, List<CountBucket> regionsPerArticle,
		ProbabilityHistogram topicProbabilities, ProbabilityHistogram regionProbabilities,
		ProbabilityHistogram formatProbabilities, List<ValueBucket> importance, long importanceMissing,
		TokenSummary inputTokens) {

	public EvaluationOverview {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(topicStatus, "topicStatus");
		topics = List.copyOf(topics);
		topicCategories = List.copyOf(topicCategories);
		regions = List.copyOf(regions);
		formats = List.copyOf(formats);
		topicsPerArticle = List.copyOf(topicsPerArticle);
		regionsPerArticle = List.copyOf(regionsPerArticle);
		Objects.requireNonNull(topicProbabilities, "topicProbabilities");
		Objects.requireNonNull(regionProbabilities, "regionProbabilities");
		Objects.requireNonNull(formatProbabilities, "formatProbabilities");
		importance = List.copyOf(importance);
		Objects.requireNonNull(inputTokens, "inputTokens");
	}

}
