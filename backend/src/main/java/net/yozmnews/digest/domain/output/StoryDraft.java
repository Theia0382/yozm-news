package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 읽은 주요 뉴스 하나 — 인용은 별칭
 */
public record StoryDraft(String title, String summary, List<String> refs) {

	public StoryDraft {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		refs = List.copyOf(refs);
	}

}
