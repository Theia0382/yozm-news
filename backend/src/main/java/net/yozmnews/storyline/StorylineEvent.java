package net.yozmnews.storyline;

import java.util.Objects;

import net.yozmnews.event.EventView;

/**
 * 스토리라인의 사건 하나 — 타임라인의 한 줄 · docs/specs/storyline.md §3 · storyline-R33.
 *
 * @param direct 그 스토리라인의 직접 소속이면 true, 하위 스토리라인에서 물려받은 사건이면 false — 둘 다면 true
 */
public record StorylineEvent(EventView event, boolean direct) {

	public StorylineEvent {
		Objects.requireNonNull(event, "event");
	}

}
