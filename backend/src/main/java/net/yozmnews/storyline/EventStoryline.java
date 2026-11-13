package net.yozmnews.storyline;

import java.util.Objects;

/**
 * 사건이 든 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R33.
 *
 * @param direct 사건이 직접 든 스토리라인이면 true, 그 상위라서 든 것이면 false — 둘 다면 true
 */
public record EventStoryline(StorylineView storyline, boolean direct) {

	public EventStoryline {
		Objects.requireNonNull(storyline, "storyline");
	}

}
