package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

/**
 * 스토리라인 목록의 다음 쪽 시작 — 앞 쪽 마지막 스토리라인의 (마지막 기사 시각, 스토리라인 ID) · docs/specs/storyline.md §3
 */
public record StorylineCursor(Instant lastArticleAt, StorylineId id) {

	public StorylineCursor {
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(id, "id");
	}

}
