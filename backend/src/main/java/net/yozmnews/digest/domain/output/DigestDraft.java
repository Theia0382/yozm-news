package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 읽은 출력 — 항목 대조 전 · docs/specs/digest.md §4
 */
public record DigestDraft(String overview, List<StoryDraft> topStories, List<HeadlineDraft> headlines) {

	public DigestDraft {
		Objects.requireNonNull(overview, "overview");
		topStories = List.copyOf(topStories);
		headlines = List.copyOf(headlines);
	}

}
