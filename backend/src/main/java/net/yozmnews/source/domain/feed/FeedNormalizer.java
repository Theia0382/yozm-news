package net.yozmnews.source.domain.feed;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.profile.CollectionProfile;

/**
 * 피드 문서를 수집 프로필에 따라 정규화한다 — 문자 인코딩 판정 → 사전 보정 → XML 읽기(DTD 를 해석하지 않음) → Rome 형식 판별 →
 * 항목마다 필드 보정. 상태가 없고 호출마다 파서를 새로 만든다.
 */
public final class FeedNormalizer {

	public FeedNormalizer() {
	}

	public FeedNormalization normalize(FeedDocument document, CollectionProfile profile) {
		return switch (DocumentDecoder.decode(document, profile.preCorrections())) {
			case DocumentDecoder.Decoded.Failed failed -> new FeedNormalization.Unreadable(failed.problem());
			case DocumentDecoder.Decoded.Text decoded -> switch (FeedXmlReader
				.read(PreCorrector.apply(decoded.text(), profile.preCorrections()))) {
				case FeedXmlReader.Read.Failed failed -> new FeedNormalization.Unreadable(failed.problem());
				case FeedXmlReader.Read.Parsed parsed -> switch (RomeFeed.build(parsed.document())) {
					case RomeFeed.Built.Failed failed -> new FeedNormalization.Unreadable(failed.problem());
					case RomeFeed.Built.Feed feed -> items(feed, document, profile, decoded.replacedCharacters());
				};
			};
		};
	}

	private static FeedNormalization.Normalized items(RomeFeed.Built.Feed feed, FeedDocument document,
			CollectionProfile profile, int replacedCharacters) {
		List<FetchedArticle> articles = new ArrayList<>();
		List<SkippedItem> skipped = new ArrayList<>();
		for (int i = 0; i < feed.entries().size(); i++) {
			switch (ItemNormalizer.normalize(feed.entries().get(i), feed.format(), profile, document.fetchedAt())) {
				case ItemNormalizer.Outcome.Article article -> articles.add(article.article());
				case ItemNormalizer.Outcome.Skipped skip -> skipped.add(new SkippedItem(i + 1, skip.reason()));
			}
		}
		return new FeedNormalization.Normalized(articles, skipped, replacedCharacters);
	}

}
