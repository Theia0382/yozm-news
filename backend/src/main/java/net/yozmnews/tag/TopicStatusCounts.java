package net.yozmnews.tag;

import java.util.Objects;

/**
 * 평가 현황의 주제 판정별 평가 수 — {@link TopicStatus} 마다 · docs/specs/tag.md §3
 */
public record TopicStatusCounts(Share tagged, Share lowConfidence, Share unclassified) {

	public TopicStatusCounts {
		Objects.requireNonNull(tagged, "tagged");
		Objects.requireNonNull(lowConfidence, "lowConfidence");
		Objects.requireNonNull(unclassified, "unclassified");
	}

}
