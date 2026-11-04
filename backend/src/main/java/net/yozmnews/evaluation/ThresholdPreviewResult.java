package net.yozmnews.evaluation;

import java.util.Objects;
import java.util.Set;

/**
 * 기준 미리보기의 결과 — 아무것도 쓰지 않는다 · docs/specs/evaluation.md §3 · evaluation-R35.
 */
public sealed interface ThresholdPreviewResult {

	/**
	 * 제안 값이 지금 값과 다르다.
	 * @param warnings 그 변경이 낼 경고(evaluation-R12) — 없으면 빈 집합
	 * @param evaluations 기간 안 활성 버전 평가의 수
	 * @param changed 그 태그 종류의 판정이 두 기준에서 다른 평가의 수
	 * @param before 지금 기준의 평가 현황
	 * @param after 그 태그 종류만 제안 값으로 바꾼 기준의 평가 현황 — 확률 분포 칸의 저장 범위는 제안 저장 기준으로, 저장 기준 바뀜 표시는 지금
	 * 이력 그대로다
	 */
	record Previewed(TagSetId version, TagThresholds current, TagThresholds proposed, Set<ThresholdWarning> warnings,
			long evaluations, long changed, EvaluationOverview before, EvaluationOverview after)
			implements ThresholdPreviewResult {

		public Previewed {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(current, "current");
			Objects.requireNonNull(proposed, "proposed");
			warnings = Set.copyOf(warnings);
			Objects.requireNonNull(before, "before");
			Objects.requireNonNull(after, "after");
		}

	}

	/** 제안 값이 지금 값과 같다 — 그 활성 버전과 지금 값 */
	record Unchanged(TagSetId version, TagThresholds current) implements ThresholdPreviewResult {

		public Unchanged {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(current, "current");
		}

	}

	/** 제안 값이 0 ~ 1 · 소수 셋째 자리 밖이다 */
	record OutOfRange() implements ThresholdPreviewResult {
	}

}
