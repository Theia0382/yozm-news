package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.source.FeedStatus;
import org.jspecify.annotations.Nullable;

/**
 * 피드 상태 알림의 피드 한 줄 — docs/specs/user.md §3 · user-R23
 *
 * @param feedId 피드 ID
 * @param sourceName 출처 이름 — 남길 때 얻지 못했으면 null
 * @param section 섹션 — 남길 때 얻지 못했으면 null
 * @param firstFrom 알림에 모은 첫 전이의 이전 상태
 * @param lastTo 알림에 모은 마지막 전이의 새 상태
 * @param firstAt 첫 전이의 판정 시각
 * @param lastAt 마지막 전이의 판정 시각
 * @param changes 모은 전이 수
 */
record FeedStatusEntryResponse(long feedId, @Nullable String sourceName, @Nullable String section,
		FeedStatus firstFrom, FeedStatus lastTo, Instant firstAt, Instant lastAt, int changes) {
}
