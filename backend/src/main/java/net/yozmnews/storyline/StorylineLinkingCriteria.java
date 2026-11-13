package net.yozmnews.storyline;

import java.time.Duration;
import java.util.Objects;

/**
 * 잇기 기준의 값 — 시간 창과 기준 점수 · docs/specs/storyline.md §3 · storyline-R9.
 */
public record StorylineLinkingCriteria(Duration window, double threshold) {

	public StorylineLinkingCriteria {
		Objects.requireNonNull(window, "window");
	}

}
