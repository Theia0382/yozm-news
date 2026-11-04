package net.yozmnews.admin.adapter.in.web;

import java.util.Set;

import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdWarning;
import org.jspecify.annotations.Nullable;

/**
 * 기준 변경의 결과 — docs/specs/admin.md §3 · admin-R1 · evaluation-R11 · evaluation-R12.
 * <p>
 * {@code CHANGED} 면 이전 값 · 새 값 · 경고, {@code UNCHANGED} 면 지금 값이 있다. 범위 밖 값은 결과가 아니라 400 이다.
 *
 * @param version 판정한 활성 버전 — JSON 수. 미리보기 뒤 활성 전환이 끼었으면 미리보기의 버전과 다르다
 */
record ThresholdChangeResponse(Outcome result, long version, @Nullable TagThresholds previous, TagThresholds current,
		Set<ThresholdWarning> warnings) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 400 — 값이 범위 밖일 때
	 */
	static ThresholdChangeResponse of(ThresholdChangeResult result) {
		return switch (result) {
			case ThresholdChangeResult.Changed changed -> new ThresholdChangeResponse(Outcome.CHANGED,
					changed.version().value(), changed.previous(), changed.current(), changed.warnings());
			case ThresholdChangeResult.Unchanged unchanged -> new ThresholdChangeResponse(Outcome.UNCHANGED,
					unchanged.version().value(), null, unchanged.current(), Set.of());
			case ThresholdChangeResult.OutOfRange outOfRange -> throw ThresholdValuesProblem.outOfRange();
		};
	}

	enum Outcome {

		/** 바꿨다 */
		CHANGED,
		/** 지금 값과 같아 바꾸지 않았다 */
		UNCHANGED

	}

}
