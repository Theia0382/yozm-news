package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.event.AssignmentCriteria;

/**
 * 이 프로세스의 사건 배정 기준 — 설정이라 화면에서 바꾸지 않는다 · docs/specs/admin.md §5 admin-R7 · event-R17.
 *
 * @param windowSeconds 시간 창(초)
 */
record AssignmentCriteriaResponse(long windowSeconds, double upper, double lower, double store, double probability) {

	static AssignmentCriteriaResponse of(AssignmentCriteria criteria) {
		return new AssignmentCriteriaResponse(criteria.window().toSeconds(), criteria.upper(), criteria.lower(),
				criteria.store(), criteria.probability());
	}

}
