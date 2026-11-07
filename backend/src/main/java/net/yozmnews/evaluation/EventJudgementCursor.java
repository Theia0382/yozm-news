package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 판정 원본 목록의 다음 쪽 시작 — 앞 쪽 마지막 원본의 (판정 시각, ID) · docs/specs/evaluation.md §3
 */
public record EventJudgementCursor(Instant judgedAt, EventJudgementId id) {

	public EventJudgementCursor {
		Objects.requireNonNull(judgedAt, "judgedAt");
		Objects.requireNonNull(id, "id");
	}

}
