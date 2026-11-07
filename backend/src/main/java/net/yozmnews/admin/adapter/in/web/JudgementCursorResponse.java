package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.EventJudgementCursor;

/**
 * 판정 기록의 다음 쪽 커서 — 질의 {@code afterJudgedAt} · {@code afterId} 로 되돌려 준다 · docs/specs/admin.md §3.
 */
record JudgementCursorResponse(Instant judgedAt, long id) {

	static JudgementCursorResponse of(EventJudgementCursor cursor) {
		return new JudgementCursorResponse(cursor.judgedAt(), cursor.id().value());
	}

}
