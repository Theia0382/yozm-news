package net.yozmnews.reader.adapter.in.web;

import java.util.List;

/**
 * 스토리라인 ID 여럿의 카드 — 받은 순서, 없는 ID 는 빠진다. docs/specs/reader.md §5 reader-R6.
 */
record StorylineCardsResponse(List<StorylineCardEntryResponse> cards) {
}
