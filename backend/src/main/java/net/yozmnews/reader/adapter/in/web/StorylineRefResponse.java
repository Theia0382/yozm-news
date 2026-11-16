package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.storyline.StorylineState;
import org.jspecify.annotations.Nullable;

/**
 * 관계로 이어진 스토리라인 — ID(합쳐졌으면 남는 쪽) · 대표 기사 제목 · 상태. docs/specs/reader.md §5 reader-R4.
 *
 * @param title 대표 기사 제목 — 없으면 null
 */
record StorylineRefResponse(long id, @Nullable String title, StorylineState state) {
}
