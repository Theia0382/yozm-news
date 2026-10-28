package net.yozmnews.article;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;

/** 시험용 {@link ArticleFetched} — 받은 시각은 발행 10분 뒤, 카테고리는 {@code 정치}. */
final class Fetched {

	private Fetched() {
	}

	static ArticleFetched item(long sourceId, long feedId, String link, @Nullable String guid, String title,
			Instant publishedAt) {
		return new ArticleFetched(new SourceId(sourceId), new FeedId(feedId), publishedAt.plus(Duration.ofMinutes(10)),
				new FetchedArticle(title, URI.create(link), guid, "요약", publishedAt, false, "홍길동", List.of("정치"),
						null));
	}

}
