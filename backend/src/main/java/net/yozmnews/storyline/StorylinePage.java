package net.yozmnews.storyline;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 목록 한 쪽 · docs/specs/storyline.md §3.
 *
 * @param next 다음 쪽의 커서 — 더 없으면 null
 */
public record StorylinePage(List<StorylineSummary> storylines, @Nullable StorylineCursor next) {

	public StorylinePage {
		storylines = List.copyOf(storylines);
	}

}
