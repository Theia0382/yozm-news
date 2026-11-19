package net.yozmnews.digest.domain.output;

import java.util.Objects;

/**
 * 읽은 주요 뉴스 하나 — 항목은 별칭
 *
 * @param ref 항목 별칭
 * @param title 제목
 * @param summary 요약
 */
public record StoryDraft(String ref, String title, String summary) {

	public StoryDraft {
		Objects.requireNonNull(ref, "ref");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
	}

}
