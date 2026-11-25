package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 하나의 타임라인 — 카드 · 상위 · 하위 · 앞 스토리라인 · 이어진 스토리라인 · 사건. docs/specs/reader.md §5 reader-R4 · R5. 판정 근거는 싣지 않는다.
 *
 * @param storyline 남는 스토리라인의 카드 — 경로 ID 가 합쳐진 스토리라인이면 ID 가 다르다
 * @param parent 상위 — 없거나 그새 없어졌으면 null
 * @param children 이 스토리라인이 포함하는 하위
 * @param preceding 이 스토리라인을 낳은 앞 스토리라인
 * @param following 이 스토리라인이 낳은 이어진 스토리라인
 * @param events 닫은 사건 집합 — 첫 기사 시각 · 사건 ID 순
 */
record StorylineTimelineResponse(StorylineCardResponse storyline, @Nullable StorylineRefResponse parent,
		List<StorylineRefResponse> children, List<StorylineRefResponse> preceding, List<StorylineRefResponse> following,
		List<TimelineEventResponse> events) {
}
