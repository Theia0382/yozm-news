package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 항목과 대조한 출력 — 주요 뉴스나 헤드라인이 하나 이상, 각각 항목 순서 · docs/specs/digest.md §5 · digest-R6
 */
public record CitedDigest(String overview, List<CitedStory> stories, List<CitedHeadline> headlines) {

	public CitedDigest {
		Objects.requireNonNull(overview, "overview");
		stories = List.copyOf(stories);
		headlines = List.copyOf(headlines);
		if (stories.isEmpty() && headlines.isEmpty()) {
			throw new IllegalArgumentException("인용이 하나도 없다");
		}
	}

}
