package net.yozmnews.user.domain.summary;

import java.util.Objects;

/**
 * 정리의 글 한 벌 — 흐름 전체 또는 꼭대기 하나. docs/specs/user.md §4
 *
 * @param title 제목
 * @param summary 흐름 요약
 * @param recent 최근 전개
 */
public record SummaryText(String title, String summary, String recent) {

	public SummaryText {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		Objects.requireNonNull(recent, "recent");
	}

}
