package net.yozmnews.reader.adapter.in.web;

/**
 * 구독 제안의 주제 중분류 하나 — docs/specs/reader.md §5 · reader-R12
 *
 * @param key 중분류 전체 키 — 수락 · 거절 요청에 싣는다
 * @param name 중분류 이름
 * @param categoryName 대분류 이름
 */
record TopicSuggestionResponse(String key, String name, String categoryName) {
}
