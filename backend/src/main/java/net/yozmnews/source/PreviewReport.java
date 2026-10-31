package net.yozmnews.source;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 미리보기 — 피드를 한 번 받아 만든 지표와 기사 표본 — docs/specs/source.md §3.
 * <p>
 * 저장하지 않는다(source-R9).
 */
public record PreviewReport(int items, int articles, List<SkippedItemView> skipped, int datedArticles,
		boolean publishedAtUnread, SummaryLengthsView summaries, int replacedCharacters, int decodedCharacters,
		@Nullable Instant latestPublishedAt, @Nullable URI movedTo, List<ArticleSampleView> samples) {

	public PreviewReport {
		skipped = List.copyOf(skipped);
		Objects.requireNonNull(summaries, "summaries");
		samples = List.copyOf(samples);
	}

}
