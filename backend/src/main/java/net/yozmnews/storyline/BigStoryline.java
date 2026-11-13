package net.yozmnews.storyline;

import java.math.BigDecimal;
import java.util.Objects;

import net.yozmnews.event.EventTally;

/**
 * 조회 기간의 큰 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R19.
 *
 * @param tally 조회 기간의 기간 집계
 * @param intensity 조회 기간의 보도 강도 — 소수 둘째 자리(storyline-R19)
 */
public record BigStoryline(StorylineView storyline, EventTally tally, BigDecimal intensity) {

	public BigStoryline {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(intensity, "intensity");
	}

}
