package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 한 버전의 주제 · 지역 두 기준 — docs/specs/evaluation.md §3
 */
public record TagSetThresholds(TagSetId versionId, TagThresholds topic, TagThresholds region) {

	public TagSetThresholds {
		Objects.requireNonNull(versionId, "versionId");
		Objects.requireNonNull(topic, "topic");
		Objects.requireNonNull(region, "region");
	}

}
