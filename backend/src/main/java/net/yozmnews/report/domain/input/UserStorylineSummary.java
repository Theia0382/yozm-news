package net.yozmnews.report.domain.input;

import java.time.Instant;
import java.util.Objects;

/**
 * 사용자 스토리라인의 정리 — 그 사용자의 LLM 키로 사용자 스토리라인 전체를 읽고 쓴 글 · docs/specs/report.md §5 · report-R16
 *
 * @param updatedAt 마지막으로 정리한 시각 — 기간 끝보다 이를 수 있다
 */
public record UserStorylineSummary(String title, String summary, String recent, Instant updatedAt) {

	public UserStorylineSummary {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		Objects.requireNonNull(recent, "recent");
		Objects.requireNonNull(updatedAt, "updatedAt");
	}

}
