package net.yozmnews.source.domain.feed;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.ItemField;
import org.jdom2.Element;
import org.jspecify.annotations.Nullable;

/**
 * 피드 항목 하나 → 기사 — docs/specs/source.md §4 · source-R40 · source-R41
 */
final class ItemNormalizer {

	/** 항목 하나의 정규화 결과 */
	sealed interface Outcome {

		/** 기사로 만들었다 */
		record Article(FetchedArticle article) implements Outcome {

			public Article {
				Objects.requireNonNull(article, "article");
			}

		}

		/** 항목을 건너뛰었다 */
		record Skipped(SkipReason reason) implements Outcome {

			public Skipped {
				Objects.requireNonNull(reason, "reason");
			}

		}

	}

	private ItemNormalizer() {
	}

	static Outcome normalize(RomeFeed.Entry entry, FeedFormat format, CollectionProfile profile, Instant fetchedAt) {
		String link = (entry.link() != null) ? entry.link().strip() : "";
		if (link.isEmpty()) {
			return new Outcome.Skipped(SkipReason.MISSING_LINK);
		}
		Optional<URI> linkUri = webUrl(link);
		if (linkUri.isEmpty()) {
			return new Outcome.Skipped(SkipReason.INVALID_LINK);
		}
		Optional<String> title = TextCleaner.text((entry.title() != null) ? entry.title() : "");
		if (title.isEmpty()) {
			return new Outcome.Skipped(SkipReason.MISSING_TITLE);
		}
		Element item = entry.element();
		Optional<Instant> publishedAt = first(candidates(item, format, profile, ItemField.PUBLISHED_AT),
				text -> PublishedAtResolver.resolve(text, profile.dates(), fetchedAt));
		return new Outcome.Article(new FetchedArticle(title.get(), linkUri.get(), guid(item, format),
				first(candidates(item, format, profile, ItemField.SUMMARY), TextCleaner::summary).orElse(null),
				publishedAt.orElse(fetchedAt), publishedAt.isEmpty(),
				first(candidates(item, format, profile, ItemField.AUTHOR), TextCleaner::text).orElse(null),
				categories(candidates(item, format, profile, ItemField.CATEGORY)),
				first(candidates(item, format, profile, ItemField.THUMBNAIL), text -> webUrl(text.strip())).orElse(null)));
	}

	private static List<List<String>> candidates(Element item, FeedFormat format, CollectionProfile profile,
			ItemField field) {
		return ItemValues.candidates(item, format, field, profile.items().get(field));
	}

	/** 읽을 곳 순서 · 문서 순서로 처음 쓸 수 있는 값을 고른다 */
	private static <T> Optional<T> first(List<List<String>> candidates, Function<String, Optional<T>> usable) {
		for (List<String> values : candidates) {
			for (String value : values) {
				Optional<T> result = usable.apply(value);
				if (result.isPresent()) {
					return result;
				}
			}
		}
		return Optional.empty();
	}

	/** 카테고리를 고른다 — source-R41 */
	private static List<String> categories(List<List<String>> candidates) {
		for (List<String> values : candidates) {
			Set<String> categories = new LinkedHashSet<>();
			values.forEach(value -> TextCleaner.text(value).ifPresent(categories::add));
			if (!categories.isEmpty()) {
				return List.copyOf(categories);
			}
		}
		return List.of();
	}

	private static @Nullable String guid(Element item, FeedFormat format) {
		String guid = StandardElements.guid(item, format);
		if (guid == null || guid.isBlank()) {
			return null;
		}
		return guid.strip();
	}

	private static Optional<URI> webUrl(String text) {
		try {
			URI uri = new URI(LinkText.encodeUnsafe(text));
			return FetchedArticle.isWebUrl(uri) ? Optional.of(uri) : Optional.empty();
		}
		catch (URISyntaxException ex) {
			return Optional.empty();
		}
	}

}
