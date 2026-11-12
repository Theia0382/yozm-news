package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Objects;

/**
 * 조회로 읽은 스토리라인 — 상태는 싣지 않는다(조회 시각으로 정한다) · docs/specs/storyline.md §4.
 *
 * @param eventCount 구성 사건 수(소속 행 수)
 * @param articleCount 표현의 기사 수(storyline-R7)
 */
public record StoredStoryline(long id, int eventCount, int articleCount, Instant firstArticleAt, Instant lastArticleAt,
		long representativeEventId, Instant createdAt) {

	public StoredStoryline {
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
