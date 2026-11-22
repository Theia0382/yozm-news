package net.yozmnews.user.domain.summary;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 정리의 재료가 되는 사건 하나 — 그 사건의 대표 기사로 나타낸다. docs/specs/user.md §4
 *
 * @param eventId 사건 ID
 * @param articleId 대표 기사 ID
 * @param firstArticleAt 사건의 첫 기사 시각
 * @param title 대표 기사의 제목
 * @param summary 대표 기사의 저장된 RSS 요약문 — 없으면 null
 */
public record SummaryEvent(long eventId, long articleId, Instant firstArticleAt, String title,
		@Nullable String summary) {

	public SummaryEvent {
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(title, "title");
	}

}
