package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.EvaluationSampleView;

/**
 * 표본 — 씨앗으로 고른 평가, 해시 순 · docs/specs/admin.md §3 · evaluation-R32.
 *
 * @param population 표본을 고른 평가의 수
 */
record EvaluationSampleResponse(long version, Instant from, Instant to, long seed, long population,
		List<EvaluationRowResponse> rows) {

	static EvaluationSampleResponse of(EvaluationSampleView sample, List<EvaluationRowResponse> rows) {
		return new EvaluationSampleResponse(sample.version().value(), sample.from(), sample.to(), sample.seed(),
				sample.population(), rows);
	}

}
