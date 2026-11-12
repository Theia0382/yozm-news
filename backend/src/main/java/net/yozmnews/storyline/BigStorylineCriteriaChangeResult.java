package net.yozmnews.storyline;

import java.util.Objects;

/**
 * 큰 판정 기준 변경의 결과 — docs/specs/storyline.md §3 · storyline-R20
 */
public sealed interface BigStorylineCriteriaChangeResult {

	/** 바꿨다 — 이전 값과 새 값 */
	record Changed(BigStorylineCriteria previous, BigStorylineCriteria current) implements BigStorylineCriteriaChangeResult {

		public Changed {
			Objects.requireNonNull(previous, "previous");
			Objects.requireNonNull(current, "current");
		}

	}

	/** 지금 값과 같아 바꾸지 않았다 */
	record Unchanged(BigStorylineCriteria current) implements BigStorylineCriteriaChangeResult {

		public Unchanged {
			Objects.requireNonNull(current, "current");
		}

	}

	/** 값이 범위 밖이라 바꾸지 않았다 — 범위는 {@link BigStorylineCriteria} */
	record OutOfRange() implements BigStorylineCriteriaChangeResult {
	}

}
