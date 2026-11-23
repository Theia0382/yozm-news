package net.yozmnews.tag;

import java.util.Objects;

/**
 * 기사 하나의 평가 가운데 한 버전의 것 — 그 버전과 번호, 평가 한 건의 판정 · docs/specs/tag.md §3.
 *
 * @param version 평가한 태그 체계 버전
 * @param versionNumber 그 버전의 번호
 * @param evaluation 그 버전의 지금 태그 기준 · 저장 기준으로 조회 때 판정한 평가
 */
public record VersionedEvaluationRow(TagSetId version, int versionNumber, EvaluationRow evaluation) {

	public VersionedEvaluationRow {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(evaluation, "evaluation");
	}

}
