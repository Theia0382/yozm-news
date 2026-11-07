package net.yozmnews.event;

import java.util.Objects;

/**
 * 큰 사건 기준 변경의 결과 — docs/specs/event.md §3 · event-R21
 */
public sealed interface BigEventCriteriaChangeResult {

	/** 바꿨다 — 이전 값과 새 값 */
	record Changed(BigEventCriteria previous, BigEventCriteria current) implements BigEventCriteriaChangeResult {

		public Changed {
			Objects.requireNonNull(previous, "previous");
			Objects.requireNonNull(current, "current");
		}

	}

	/** 지금 값과 같아 바꾸지 않았다 */
	record Unchanged(BigEventCriteria current) implements BigEventCriteriaChangeResult {

		public Unchanged {
			Objects.requireNonNull(current, "current");
		}

	}

	/** 값이 범위 밖이라 바꾸지 않았다 — 범위는 {@link BigEventCriteria} */
	record OutOfRange() implements BigEventCriteriaChangeResult {
	}

}
