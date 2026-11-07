package net.yozmnews.evaluation;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 판정 원본 목록 한 쪽 · docs/specs/evaluation.md §3.
 *
 * @param next 다음 쪽의 커서 — 더 없으면 null
 */
public record EventJudgementPage(List<EventJudgementView> judgements, @Nullable EventJudgementCursor next) {

	public EventJudgementPage {
		judgements = List.copyOf(judgements);
	}

}
