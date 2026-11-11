package net.yozmnews.tag.domain.threshold;

import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.ThresholdWarning;

/**
 * 태그 기준 · 저장 기준 변경 판정의 결과 — docs/specs/tag.md §4 · tag-R11 · tag-R12
 */
public sealed interface ThresholdDecision {

	/** 지금 값과 같다 — 바꾸지 않고 이력도 남기지 않는다 */
	record Unchanged() implements ThresholdDecision {
	}

	/** 범위 밖이다 — 바꾸지 않는다 */
	record OutOfRange() implements ThresholdDecision {
	}

	/** 바꾼다 — 새 값과, 이번 변경이 만든 어긋남의 경고(없으면 빈 집합) */
	record Change(ThresholdValues next, Set<ThresholdWarning> warnings) implements ThresholdDecision {

		public Change {
			Objects.requireNonNull(next, "next");
			warnings = Set.copyOf(warnings);
		}

	}

}
