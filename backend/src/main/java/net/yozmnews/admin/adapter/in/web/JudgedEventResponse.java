package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.evaluation.JudgedEvent;

/**
 * 판정 기록의 물은 후보 하나 — 판정 당시의 사건 ID 와 확률 · docs/specs/admin.md §5 admin-R7.
 */
record JudgedEventResponse(long eventId, double probability) {

	static JudgedEventResponse of(JudgedEvent candidate) {
		return new JudgedEventResponse(candidate.eventId(), candidate.probability());
	}

}
