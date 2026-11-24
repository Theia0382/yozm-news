package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.TopicSubscription;
import org.jspecify.annotations.Nullable;

/**
 * 선별 규칙 시험의 재료 — 손으로 적은 기사 · 사건 · 사용자. 기본 기사는 정치 행정 · 국내 · 스트레이트 · 소스 1 · 발행 2026-11-16T12:00Z 다.
 * 사건의 값은 {@link EventFacts#of} 가 소속 기사의 평가로 센다 — 기사 하나뿐인 사건은 그 기사의 주제 · 지역 · 중요도를 그대로 갖는다.
 */
final class Facts {

	static final Instant PUBLISHED = Instant.parse("2026-11-16T12:00:00Z");

	private Facts() {
	}

	static Builder article(long id) {
		return new Builder(id);
	}

	/** 기사 하나뿐인 사건 — 사건 ID 는 기사 ID 와 같다 */
	static CandidateEvent single(Builder article) {
		return event(article.id, article);
	}

	/** 사건 — 기사가 모두 평가 있는 소속 기사이고 모두 후보 기사(기간에 든 · 제외를 지난 기사)다 */
	static CandidateEvent event(long id, Builder... articles) {
		return event(id, List.of(articles), List.of(articles));
	}

	/**
	 * 사건 — 사건의 값은 {@code members} 전부로 세고, 후보 기사는 {@code inPeriod} 다
	 * @param members 평가 있는 소속 기사 전부
	 * @param inPeriod 그 가운데 기간에 든 · 제외를 지난 기사
	 */
	static CandidateEvent event(long id, List<Builder> members, List<Builder> inPeriod) {
		return new CandidateEvent(eventFacts(id, members), List.of(), inPeriod.stream().map(Builder::build).toList());
	}

	/** 같은 사건에 꼭대기를 실은 것 */
	static CandidateEvent withTops(CandidateEvent event, StorylineId... tops) {
		return new CandidateEvent(event.facts(), List.of(tops), event.articles());
	}

	/** 사건의 값 — 소속 기사의 평가로 센다 */
	static EventFacts eventFacts(long id, List<Builder> members) {
		return EventFacts.of(new EventId(id), members.stream().map(Builder::evaluation).toList());
	}

	/** 구독 행 — 대분류 politics(행정 · 입법), 중분류 economy.markets. 제외 없음 */
	static UserFilter politicsAndMarkets() {
		return new UserFilter(List.of(new TopicSubscription("economy.markets", Set.of("economy.markets")),
				new TopicSubscription("politics", Set.of("politics.executive", "politics.legislature"))), Set.of(),
				Set.of(), Set.of(), Set.of(), Set.of());
	}

	static DigestInterests interests(List<TopicSubscription> topics, Set<String> excludedRegions,
			Set<String> excludedFormats, Set<SourceId> excludedSources) {
		return new DigestInterests(new TagSetId(1), topics, excludedRegions, excludedFormats, excludedSources,
				ZoneId.of("Asia/Seoul"), 60, 3, SummaryLength.NORMAL, ReasoningEffort.MEDIUM);
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

		/** 같은 값의 활성 키 평가 — 사건의 값({@link EventFacts})의 재료 */
		ActiveEvaluation evaluation() {
			return new ActiveEvaluation(new ArticleId(this.id), this.subtopics, this.regions, this.formats, this.status,
					this.importance);
		}

		ArticleFacts build() {
			return new ArticleFacts(new ArticleId(this.id), this.subtopics, this.regions, this.formats, this.status,
					this.importance, this.sources, this.published);
		}

	}

}
