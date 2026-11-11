package net.yozmnews.tag.domain.result;

import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;

/**
 * 태그 기준 · 저장 기준과 체계의 버전 맞음 — {@link EvaluationRecord} · {@link TagJudgement} · 평가 현황 집계의 사전 조건의 정본.
 */
public final class VersionMatch {

	private VersionMatch() {
	}

	/**
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다를 때
	 */
	public static void require(TagSetThresholds thresholds, TagSetView tagSet) {
		if (!thresholds.versionId().equals(tagSet.id())) {
			throw new IllegalArgumentException("태그 기준 · 저장 기준의 버전이 체계와 다르다: " + thresholds.versionId().value() + " · "
					+ tagSet.id().value());
		}
	}

}
