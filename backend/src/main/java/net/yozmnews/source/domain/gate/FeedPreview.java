package net.yozmnews.source.domain.gate;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.feed.SkippedItem;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.ItemField;
import org.jspecify.annotations.Nullable;

/**
 * 미리보기 — 피드를 켜기 전에 한 번 받아 만든 지표 · docs/specs/source.md §4 · source-R9.
 *
 * @param articles 기사 수
 * @param skipped 기사로 넘기지 않은 항목 — 문서 순서
 * @param datedArticles 발행 시각을 해석한(추정이 아닌) 기사 수
 * @param publishedAtUnread 프로필이 발행 시각의 읽을 곳을 빈 목록으로 선언했다 — 날짜 기준을 보지 않는다
 * @param replacedCharacters 읽을 수 없는 바이트를 U+FFFD 로 바꾼 자리 수
 * @param decodedCharacters 받은 바이트를 문자로 읽은 결과의 코드 포인트 수
 * @param latestPublishedAt 추정이 아닌 발행 시각의 최댓값 — 없으면 null
 * @param movedTo 받은 결과가 거친 영구 이동 주소 — 없으면 null
 * @param samples 기사 앞 {@link #SAMPLE_SIZE} 건 — 문서 순서
 */
public record FeedPreview(int articles, List<SkippedItem> skipped, int datedArticles, boolean publishedAtUnread,
		SummaryLengths summaries, int replacedCharacters, int decodedCharacters, @Nullable Instant latestPublishedAt,
		@Nullable URI movedTo, List<ArticleSample> samples) {

	/** 표본 수 — 정본 */
	public static final int SAMPLE_SIZE = 5;

	public FeedPreview {
		skipped = List.copyOf(skipped);
		Objects.requireNonNull(summaries, "summaries");
		samples = List.copyOf(samples);
	}

	/** 받은 결과와 그 피드를 읽은 프로필로 미리보기를 만든다 */
	public static FeedPreview of(FetchResult.Fetched fetched, CollectionProfile profile) {
		FeedNormalization.Normalized normalization = fetched.normalization();
		List<FetchedArticle> articles = normalization.articles();
		List<FetchedArticle> dated = articles.stream().filter(article -> !article.publishedAtEstimated()).toList();
		@Nullable Instant latest = dated.stream()
			.map(FetchedArticle::publishedAt)
			.max(Comparator.naturalOrder())
			.orElse(null);
		List<ArticleSample> samples = articles.stream()
			.limit(SAMPLE_SIZE)
			.map(article -> new ArticleSample(article.title(), article.publishedAt(), article.publishedAtEstimated()))
			.toList();
		return new FeedPreview(articles.size(), normalization.skipped(), dated.size(),
				profile.items().get(ItemField.PUBLISHED_AT).isEmpty(), SummaryLengths.of(articles),
				normalization.replacedCharacters(), normalization.decodedCharacters(), latest, fetched.movedTo(),
				samples);
	}

	/** 항목 수 — 기사 + 건너뛴 항목 */
	public int items() {
		return this.articles + this.skipped.size();
	}

}
