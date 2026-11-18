package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.TopicSubscription;
import org.jspecify.annotations.Nullable;

/**
 * 선별 규칙 시험의 재료 — 손으로 적은 기사 · 사용자. 기본 기사는 정치 행정 · 국내 · 스트레이트 · 소스 1 · 발행 2026-11-16T12:00Z 다.
 */
final class Facts {

	static final Instant PUBLISHED = Instant.parse("2026-11-16T12:00:00Z");

	private Facts() {
	}

	static Builder article(long id) {
		return new Builder(id);
	}

	/** 구독 행 — 대분류 politics(행정 · 입법), 중분류 economy.markets. 제외 없음 */
	static UserFilter politicsAndMarkets() {
		return new UserFilter(List.of(new TopicSubscription("economy.markets", Set.of("economy.markets")),
				new TopicSubscription("politics", Set.of("politics.executive", "politics.legislature"))), Set.of(),
				Set.of(), Set.of());
	}

	static DigestInterests interests(List<TopicSubscription> topics, Set<String> excludedRegions,
			Set<String> excludedFormats, Set<SourceId> excludedSources) {
		return new DigestInterests(new TagSetId(1), topics, excludedRegions, excludedFormats, excludedSources, 0, 60, 3);
	}

	static final class Builder {

		private final long id;

		private Set<String> subtopics = Set.of("politics.executive");

		private Set<String> regions = Set.of("domestic");

		private Set<String> formats = Set.of("straight");

		private TopicStatus status = TopicStatus.TAGGED;

		private @Nullable Double importance = 3.0;

		private Set<SourceId> sources = Set.of(new SourceId(1));

		private Instant published = PUBLISHED;

		private boolean disliked;

		private Builder(long id) {
			this.id = id;
		}

		Builder subtopics(String... keys) {
			this.subtopics = Set.of(keys);
			return this;
		}

		Builder regions(String... keys) {
			this.regions = Set.of(keys);
			return this;
		}

		Builder formats(String... keys) {
			this.formats = Set.of(keys);
			return this;
		}

		Builder lowConfidence() {
			this.status = TopicStatus.LOW_CONFIDENCE;
			return this;
		}

		Builder unclassified() {
			this.status = TopicStatus.UNCLASSIFIED;
			this.subtopics = Set.of();
			return this;
		}

		Builder importance(@Nullable Double importance) {
			this.importance = importance;
			return this;
		}

		Builder sources(long... ids) {
			this.sources = java.util.Arrays.stream(ids).mapToObj(SourceId::new).collect(java.util.stream.Collectors.toSet());
			return this;
		}

		Builder published(String instant) {
			this.published = Instant.parse(instant);
			return this;
		}

		Builder disliked() {
			this.disliked = true;
			return this;
		}

		ArticleFacts build() {
			return new ArticleFacts(new ArticleId(this.id), this.subtopics, this.regions, this.formats, this.status,
					this.importance, this.sources, this.published, this.disliked);
		}

	}

}
