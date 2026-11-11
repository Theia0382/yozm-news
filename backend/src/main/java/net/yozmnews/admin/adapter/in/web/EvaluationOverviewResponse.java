package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.CountBucket;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.ProbabilityHistogram;
import net.yozmnews.tag.TagShare;
import net.yozmnews.tag.TokenSummary;
import net.yozmnews.tag.TopicStatusCounts;
import net.yozmnews.tag.ValueBucket;

/**
 * 평가 현황 — docs/specs/admin.md §3. {@code tag} 의 결과를 옮기기만 한다 — 버전 ID 는 JSON 수다(admin-R1), 수 · 비율 · 칸의
 * 값 레코드는 그대로 싣는다.
 *
 * @param from 집계 기간의 시작(포함)
 * @param to 집계 기간의 끝(제외)
 * @param evaluations 평가 수 — 비율의 분모
 */
record EvaluationOverviewResponse(long version, Instant from, Instant to, long evaluations,
		TopicStatusCounts topicStatus, List<TagShare> topics, List<TagShare> topicCategories, List<TagShare> regions,
		List<TagShare> formats, List<CountBucket> topicsPerArticle, List<CountBucket> regionsPerArticle,
		ProbabilityHistogram topicProbabilities, ProbabilityHistogram regionProbabilities,
		ProbabilityHistogram formatProbabilities, List<ValueBucket> importance, long importanceMissing,
		TokenSummary inputTokens) {

	static EvaluationOverviewResponse of(EvaluationOverview overview) {
		return new EvaluationOverviewResponse(overview.version().value(), overview.from(), overview.to(),
				overview.evaluations(), overview.topicStatus(), overview.topics(), overview.topicCategories(),
				overview.regions(), overview.formats(), overview.topicsPerArticle(), overview.regionsPerArticle(),
				overview.topicProbabilities(), overview.regionProbabilities(), overview.formatProbabilities(),
				overview.importance(), overview.importanceMissing(), overview.inputTokens());
	}

}
