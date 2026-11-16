package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 목록 기간의 스토리라인 한 쪽 — docs/specs/reader.md §5 reader-R1 · R2.
 *
 * @param from 쓴 기간의 시작(포함)
 * @param until 쓴 기간의 끝(제외) — 다음 쪽 요청에 그대로 넘긴다
 * @param next 다음 쪽 커서 — 더 없으면 null
 */
record StorylinePageResponse(StorylineWindow window, Instant from, Instant until,
		List<StorylineCardResponse> storylines, @Nullable StorylinePageCursorResponse next) {
}
