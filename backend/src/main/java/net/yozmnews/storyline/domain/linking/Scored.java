package net.yozmnews.storyline.domain.linking;

import java.util.Objects;

/**
 * 점수를 매긴 것 하나 — 스토리라인이나 사건의 ID 와 그 점수 · docs/specs/storyline.md §4.
 */
public record Scored(long id, Score score) {

	public Scored {
		Objects.requireNonNull(score, "score");
	}

}
