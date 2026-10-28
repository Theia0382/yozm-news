package net.yozmnews.article.domain;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;

/**
 * 들어온 항목 하나 — {@link ArticleFetched} 와 그 항목의 같은 기사 판정 키.
 *
 * @param sourceId 소스
 * @param feedId 항목을 받은 피드
 * @param fetchedAt 받은 수집의 시각 — 처음 발견한 시각 · 그 피드에서 처음 본 시각
 * @param article 정규화된 기사
 * @param canonicalUrl 링크의 정규 URL
 * @param titleKey 제목 비교 키
 * @param guidHash guid 원문의 해시 — guid 가 없으면 없다
 */
public record IncomingItem(SourceId sourceId, FeedId feedId, Instant fetchedAt, FetchedArticle article,
		CanonicalUrl canonicalUrl, TitleKey titleKey, @Nullable KeyHash guidHash) {

	public IncomingItem {
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(fetchedAt, "fetchedAt");
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(canonicalUrl, "canonicalUrl");
		Objects.requireNonNull(titleKey, "titleKey");
	}

	/** 판정 키는 여기 한 곳에서 계산한다 */
	public static IncomingItem from(ArticleFetched event) {
		FetchedArticle article = event.article();
		String guid = article.guid();
		return new IncomingItem(event.sourceId(), event.feedId(), event.fetchedAt(), article,
				CanonicalUrl.of(article.link()), TitleKey.of(article.title()), (guid != null) ? KeyHash.of(guid) : null);
	}

}
