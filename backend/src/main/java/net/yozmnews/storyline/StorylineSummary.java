package net.yozmnews.storyline;

import java.math.BigDecimal;
import java.util.Objects;

import net.yozmnews.event.EventTally;

/**
 * 스토리라인 목록의 한 줄 · docs/specs/storyline.md §3 · storyline-R22.
 *
 * @param tally 목록 기간의 기간 집계(storyline-R19)
 * @param intensity 목록 기간의 보도 강도 — 소수 둘째 자리(storyline-R19)
 * @param big 목록 기간 · 지금 기준으로 큰 스토리라인인가
 */
public record StorylineSummary(StorylineView storyline, EventTally tally, BigDecimal intensity, boolean big) {

	public StorylineSummary {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(intensity, "intensity");
	}

}
