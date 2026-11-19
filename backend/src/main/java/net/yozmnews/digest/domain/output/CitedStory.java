package net.yozmnews.digest.domain.output;

import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;

/**
 * 항목과 대조한 주요 뉴스 — 인용은 항목의 표본 기사다
 *
 * @param item 주요 뉴스 항목
 * @param title 제목
 * @param summary 요약
 */
public record CitedStory(AliasedItem item, String title, String summary) {

	public CitedStory {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
	}

}
