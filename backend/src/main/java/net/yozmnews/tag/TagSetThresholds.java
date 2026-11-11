package net.yozmnews.tag;

import java.util.Objects;

/**
 * 한 버전의 주제 · 지역 태그 기준 · 저장 기준 — docs/specs/tag.md §3
 */
public record TagSetThresholds(TagSetId versionId, TagThresholds topic, TagThresholds region) {

	public TagSetThresholds {
		Objects.requireNonNull(versionId, "versionId");
		Objects.requireNonNull(topic, "topic");
		Objects.requireNonNull(region, "region");
	}

}
