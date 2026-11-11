package net.yozmnews.admin.adapter.in.web;

import java.util.Set;

import net.yozmnews.tag.TagThresholds;
import net.yozmnews.tag.ThresholdPreviewResult;
import net.yozmnews.tag.ThresholdWarning;
import org.jspecify.annotations.Nullable;

/**
 * 기준 미리보기의 결과 — docs/specs/admin.md §3 · admin-R1 · tag-R35.
 * <p>
 * {@code PREVIEWED} 면 제안 값 · 경고 · 평가 수 · 판정이 달라지는 평가 수 · 두 기준의 평가 현황이 있다. {@code UNCHANGED} 는 지금 값만
 * 있다. 범위 밖 값은 결과가 아니라 400 이다.
 *
 * @param version 판정한 활성 버전 — JSON 수
 */
record ThresholdPreviewResponse(Outcome result, long version, TagThresholds current, @Nullable TagThresholds proposed,
		Set<ThresholdWarning> warnings, @Nullable Long evaluations, @Nullable Long changed,
		@Nullable EvaluationOverviewResponse before, @Nullable EvaluationOverviewResponse after) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 400 — 값이 범위 밖일 때
	 */
	static ThresholdPreviewResponse of(ThresholdPreviewResult result) {
		return switch (result) {
			case ThresholdPreviewResult.Previewed previewed -> new ThresholdPreviewResponse(Outcome.PREVIEWED,
					previewed.version().value(), previewed.current(), previewed.proposed(), previewed.warnings(),
					previewed.evaluations(), previewed.changed(), EvaluationOverviewResponse.of(previewed.before()),
					EvaluationOverviewResponse.of(previewed.after()));
			case ThresholdPreviewResult.Unchanged unchanged -> new ThresholdPreviewResponse(Outcome.UNCHANGED,
					unchanged.version().value(), unchanged.current(), null, Set.of(), null, null, null, null);
			case ThresholdPreviewResult.OutOfRange outOfRange -> throw ThresholdValuesProblem.outOfRange();
		};
	}

	enum Outcome {

		/** 제안 값이 지금 값과 다르다 */
		PREVIEWED,
		/** 제안 값이 지금 값과 같다 */
		UNCHANGED

	}

}
