package net.yozmnews.reader.adapter.in.web;

/**
 * 받은 ID 하나의 카드 — docs/specs/reader.md §5 reader-R6.
 *
 * @param requestedId 받은 스토리라인 ID
 * @param storyline 그 ID 가 가리키는 스토리라인의 카드 — 합쳐졌으면 남는 스토리라인이라 ID 가 다르다
 */
record StorylineCardEntryResponse(long requestedId, StorylineCardResponse storyline) {
}
