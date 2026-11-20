package net.yozmnews.digest.domain.output;

import java.util.Objects;

/**
 * 읽은 주요 뉴스 하나 — 항목은 그 칸의 키(항목 별칭)다
 *
 * @param title 제목
 * @param summary 요약
 */
public record StoryDraft(String title, String summary) {

	public StoryDraft {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
	}

}
