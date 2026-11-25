package net.yozmnews.user.domain.summary;

import java.util.Objects;

/**
 * 정리의 글 한 벌 — 사용자 스토리라인 전체 또는 꼭대기 하나. docs/specs/user.md §4
 * <p>
 * 꼭대기에 정리할 사건이 하나도 없으면 그 꼭대기의 글은 없다({@link #NONE}) — 세 칸이 모두 빈 문자열이다.
 *
 * @param title 제목
 * @param summary 흐름 요약
 * @param recent 최근 전개
 */
public record SummaryText(String title, String summary, String recent) {

	/** 글 없음 — 정리할 사건이 없는 꼭대기의 자리 */
	public static final SummaryText NONE = new SummaryText("", "", "");

	public SummaryText {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		Objects.requireNonNull(recent, "recent");
	}

	/** 글이 없는가 */
	public boolean none() {
		return equals(NONE);
	}

}
