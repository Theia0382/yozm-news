package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.DigestCandidate;

/**
 * 인용을 검증한 주요 뉴스 — 인용은 하나 이상, 같은 기사는 한 번
 */
public record CitedStory(String title, String summary, List<DigestCandidate> refs) {

	public CitedStory {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		refs = List.copyOf(refs);
		if (refs.isEmpty()) {
			throw new IllegalArgumentException("인용이 없는 주요 뉴스다");
		}
	}

}
