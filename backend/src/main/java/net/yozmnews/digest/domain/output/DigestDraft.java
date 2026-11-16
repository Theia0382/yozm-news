package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 읽은 출력 — 인용 검증 전 · docs/specs/digest.md §4
 */
public record DigestDraft(String overview, List<StoryDraft> topStories, List<HeadlineDraft> moreHeadlines) {

	public DigestDraft {
		Objects.requireNonNull(overview, "overview");
		topStories = List.copyOf(topStories);
		moreHeadlines = List.copyOf(moreHeadlines);
	}

}
