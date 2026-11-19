package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.tag.EvaluationQueueCounts;

/**
 * 지금의 평가 대기 · 실패 건수 — docs/specs/admin.md §3. {@code tag} 의 결과를 옮기기만 한다.
 *
 * @param waiting 실패로 남지 않은 대기 — 점유 중 포함
 * @param rejected 기사 거부로 실패로 남은 대기
 * @param retrying 실패로 남지 않은 대기 가운데 일시 실패를 한 번 이상 겪은 것 — {@code waiting} 에 든다
 */
record EvaluationQueueResponse(long waiting, long rejected, long retrying) {

	static EvaluationQueueResponse of(EvaluationQueueCounts counts) {
		return new EvaluationQueueResponse(counts.waiting(), counts.rejected(), counts.retrying());
	}

}
