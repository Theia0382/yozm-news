package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.CategoryCoverage;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.TopicCoverageView;

/**
 * 주제별 보도량과 날짜 범위의 상한 — docs/specs/admin.md §3. {@code tag} 의 결과를 옮기기만 한다 — 활성 버전 ID 는 JSON 수다
 * (admin-R1), 주제 값 레코드는 그대로 싣는다. 상한은 {@link EvaluationPeriod#MAX_DAYS} 에서 읽는다.
 *
 * @param from 집계 기간의 시작(포함)
 * @param to 집계 기간의 끝(제외)
 * @param articles 첫 평가 시각이 기간 안인 기사 수
 * @param withoutActiveTopic 그 가운데 활성 주제에 이어지지 않은 기사 수
 */
record TopicCoverageResponse(long activeVersion, Instant from, Instant to, long articles, long withoutActiveTopic,
		List<CategoryCoverage> categories, int maxDays) {

	static TopicCoverageResponse of(TopicCoverageView view) {
		return new TopicCoverageResponse(view.activeVersion().value(), view.from(), view.to(), view.articles(),
				view.withoutActiveTopic(), view.categories(), EvaluationPeriod.MAX_DAYS);
	}

}
