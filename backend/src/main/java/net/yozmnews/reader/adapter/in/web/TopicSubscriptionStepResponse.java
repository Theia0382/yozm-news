package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.user.TopicSubscriptionAction;

/**
 * 사용자가 직접 바꾼 주제 구독 하나 — docs/specs/reader.md §5
 *
 * @param at 바꾼 시각
 * @param action 더함 · 뺌
 * @param topics 그 변경이 덮는 주제 중분류 키 — 태그 체계 순
 */
record TopicSubscriptionStepResponse(Instant at, TopicSubscriptionAction action, List<String> topics) {
}
