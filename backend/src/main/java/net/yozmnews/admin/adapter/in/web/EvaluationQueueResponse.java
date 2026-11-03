package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.evaluation.EvaluationQueueCounts;

/**
 * 지금의 평가 대기 · 실패 건수 — docs/specs/admin.md §3. {@code evaluation} 의 결과를 옮기기만 한다.
 *
 * @param waiting 실패로 남지 않은 대기 — 점유 중 포함
 * @param rejected 기사 거부로 실패로 남은 대기
 * @param exhausted 일시 실패가 재시도 상한에 이르러 실패로 남은 대기
 */
record EvaluationQueueResponse(long waiting, long rejected, long exhausted) {

	static EvaluationQueueResponse of(EvaluationQueueCounts counts) {
		return new EvaluationQueueResponse(counts.waiting(), counts.rejected(), counts.exhausted());
	}

}
