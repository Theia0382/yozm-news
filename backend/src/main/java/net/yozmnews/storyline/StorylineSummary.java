package net.yozmnews.storyline;

import java.util.Objects;

import net.yozmnews.event.EventTally;

/**
 * 스토리라인 목록의 한 줄 · docs/specs/storyline.md §3 · storyline-R22.
 *
 * @param tally 목록 기간의 기간 집계(storyline-R19)
 * @param big 목록 기간 · 지금 기준으로 큰 스토리라인인가
 */
public record StorylineSummary(StorylineView storyline, EventTally tally, boolean big) {

	public StorylineSummary {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(tally, "tally");
	}

}
