package net.yozmnews.source;

import org.jspecify.annotations.Nullable;

/**
 * 피드 하나의 출처 이름 · 섹션 — docs/specs/source.md §3.
 *
 * @param sourceName 출처 이름 — 프로필의 소스 이름, 프로필을 얻지 못하면 기록된 출처 이름. 둘 다 없으면 null
 * @param section 피드 섹션 — 프로필에 그 피드가 있을 때만. 없으면 null
 */
public record FeedLabel(@Nullable String sourceName, @Nullable String section) {
}
