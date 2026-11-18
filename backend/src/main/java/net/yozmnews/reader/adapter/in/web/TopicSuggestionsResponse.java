package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 구독 제안 — docs/specs/reader.md §5 · reader-R12
 *
 * @param tagSetId 키의 태그 체계 버전 ID — 화면이 수락(태그 선호 저장) · 거절 요청에 싣는다. 계정이 없으면 null
 * @param topics 제안할 주제 중분류 — 관심 내림차순, 같으면 태그 체계 순. 없으면 빈 목록
 */
record TopicSuggestionsResponse(@Nullable Long tagSetId, List<TopicSuggestionResponse> topics) {
}
