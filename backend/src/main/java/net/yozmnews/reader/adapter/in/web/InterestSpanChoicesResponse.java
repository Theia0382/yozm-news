package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import net.yozmnews.digest.InterestHistorySpan;

/**
 * 한 단위의 고를 수 있는 기간 — docs/specs/reader.md §5
 *
 * @param unit 단위 — 주 · 월
 * @param choices 그 단위의 기간 — 기간 수 순
 * @param defaultSpan 단위를 바꿨을 때 보일 기간
 */
record InterestSpanChoicesResponse(InterestHistorySpan.Unit unit, List<InterestHistorySpan> choices,
		InterestHistorySpan defaultSpan) {
}
