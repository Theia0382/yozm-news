package net.yozmnews.storyline;

import java.time.Duration;
import java.util.Objects;

/**
 * 관계 기준의 값 — 확률 기준 · 한 사안 기준 · 사건 수 하한 · 후보 점수 기준 · 시간 창 · docs/specs/storyline.md §3 · storyline-R27 ·
 * storyline-R29.
 */
public record StorylineRelationCriteria(double probability, double oneMatter, int minEvents, double candidateScore,
		Duration window) {

	public StorylineRelationCriteria {
		Objects.requireNonNull(window, "window");
	}

}
