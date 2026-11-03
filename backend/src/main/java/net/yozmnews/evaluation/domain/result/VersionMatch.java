package net.yozmnews.evaluation.domain.result;

import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;

/**
 * 태그 기준 · 저장 기준과 체계의 버전 맞음 — {@link EvaluationRecord} · {@link TagJudgement} 의 사전 조건의 정본.
 */
final class VersionMatch {

	private VersionMatch() {
	}

	/**
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다를 때
	 */
	static void require(TagSetThresholds thresholds, TagSetView tagSet) {
		if (!thresholds.versionId().equals(tagSet.id())) {
			throw new IllegalArgumentException("태그 기준 · 저장 기준의 버전이 체계와 다르다: " + thresholds.versionId().value() + " · "
					+ tagSet.id().value());
		}
	}

}
