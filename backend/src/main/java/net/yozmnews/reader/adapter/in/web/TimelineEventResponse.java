package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * 타임라인의 사건 하나 — docs/specs/reader.md §5 reader-R4 · R5.
 *
 * @param direct 이 스토리라인의 직접 소속이면 true, 하위에서 물려받은 사건이면 false
 * @param representative 사건 기사 전체에서 고른 대표 기사 — 없으면 null
 * @param receivedOn 세션 사용자가 이 사건의 기사를 다이제스트에서 받은 가장 이른 날짜 — 받지 않았으면 null
 */
record TimelineEventResponse(long eventId, boolean direct, int articleCount, Instant firstArticleAt,
		Instant lastArticleAt, @Nullable NewsArticleResponse representative, @Nullable LocalDate receivedOn) {
}
