package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.EventStoryline;
import net.yozmnews.storyline.StorylineState;

/**
 * 사건이 든 스토리라인 하나 — docs/specs/admin.md §5 admin-R7.
 *
 * @param direct 사건이 직접 든 스토리라인이면 true, 그 상위라서 든 것이면 false
 */
record EventStorylineResponse(long id, StorylineState state, int eventCount, boolean direct) {

	static EventStorylineResponse of(EventStoryline storyline) {
		return new EventStorylineResponse(storyline.storyline().id().value(), storyline.storyline().state(),
				storyline.storyline().eventCount(), storyline.direct());
	}

}
