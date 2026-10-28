package net.yozmnews.source;

import java.time.Instant;
import java.util.Objects;

/**
 * 수집한 피드에서 새 항목 하나를 넘긴다 — source → article. 발행은 수집 작업이 한다.
 *
 * @param sourceId 소스
 * @param feedId 항목을 받은 피드
 * @param fetchedAt 이 항목을 받은 수집의 시각 — article 이 기록하는 처음 발견 시각 · 본 시각
 * @param article 정규화된 기사
 */
public record ArticleFetched(SourceId sourceId, FeedId feedId, Instant fetchedAt, FetchedArticle article) {

	public ArticleFetched {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(fetchedAt, "fetchedAt");
		Objects.requireNonNull(article, "article");
	}

}
