package net.yozmnews.evaluation;

import java.util.Objects;
import java.util.Set;

/**
 * 태그 기준 · 저장 기준 변경의 결과 — docs/specs/evaluation.md §3 · evaluation-R11 · evaluation-R12
 */
public sealed interface ThresholdChangeResult {

	/** 바꿨다 — 판정한 활성 버전, 이전 값 · 새 값과 경고(없으면 빈 집합) */
	record Changed(TagSetId version, TagThresholds previous, TagThresholds current, Set<ThresholdWarning> warnings)
			implements ThresholdChangeResult {

		public Changed {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(previous, "previous");
			Objects.requireNonNull(current, "current");
			warnings = Set.copyOf(warnings);
		}

	}

	/** 지금 값과 같아 바꾸지 않았다 — 판정한 활성 버전과 지금 값 */
	record Unchanged(TagSetId version, TagThresholds current) implements ThresholdChangeResult {

		public Unchanged {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(current, "current");
		}

	}

	/** 값이 0 ~ 1 · 소수 셋째 자리 밖이라 바꾸지 않았다 */
	record OutOfRange() implements ThresholdChangeResult {
	}

}
