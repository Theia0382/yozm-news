package net.yozmnews.storyline;

import java.util.Objects;

import net.yozmnews.event.EventTally;

/**
 * 조회 기간의 큰 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R19.
 *
 * @param tally 조회 기간의 기간 집계
 */
public record BigStoryline(StorylineView storyline, EventTally tally) {

	public BigStoryline {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(tally, "tally");
	}

}
