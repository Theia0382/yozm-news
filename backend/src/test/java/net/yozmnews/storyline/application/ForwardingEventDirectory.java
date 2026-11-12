package net.yozmnews.storyline.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventCursor;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;

/** 시험 도우미 — 사건 조회를 그대로 넘기는 대역의 바탕. 판정 중간에 끼어들거나 실패를 넣는 시험이 필요한 메서드만 바꾼다 */
class ForwardingEventDirectory implements EventDirectory {

	private final EventDirectory delegate;

	ForwardingEventDirectory(EventDirectory delegate) {
		this.delegate = delegate;
	}

	@Override
	public Optional<EventView> find(EventId id) {
		return this.delegate.find(id);
	}

	@Override
	public Map<EventId, EventView> views(Collection<EventId> ids) {
		return this.delegate.views(ids);
	}

	@Override
	public List<EventArticleView> articles(EventId id) {
		return this.delegate.articles(id);
	}

	@Override
	public Map<ArticleId, EventId> eventsOf(Collection<ArticleId> articleIds) {
		return this.delegate.eventsOf(articleIds);
	}

	@Override
	public Map<EventId, ArticleId> representatives(Map<EventId, ? extends Collection<ArticleId>> among) {
		return this.delegate.representatives(among);
	}

	@Override
	public Map<EventId, ArticleId> representativesOfAll(Collection<EventId> events) {
		return this.delegate.representativesOfAll(events);
	}

	@Override
	public Map<EventId, Integer> articleCountsIn(EventPeriod period) {
		return this.delegate.articleCountsIn(period);
	}

	@Override
	public <K> Map<K, EventTally> tallies(Map<K, ? extends Collection<EventId>> groups, EventPeriod period) {
		return this.delegate.tallies(groups, period);
	}

	@Override
	public EventPage list(EventPeriod period, @Nullable EventCursor after, int size) {
		return this.delegate.list(period, after, size);
	}

	@Override
	public Map<EventId, EventProfile> profiles(Collection<EventId> ids, int keyTerms) {
		return this.delegate.profiles(ids, keyTerms);
	}

	@Override
	public List<EventNeighbor> neighbors(EventId id, Instant from, Instant to, double minCosine, int keyTerms,
			int limit) {
		return this.delegate.neighbors(id, from, to, minCosine, keyTerms, limit);
	}

	@Override
	public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
		return this.delegate.termCounts(terms, from, to);
	}

	@Override
	public List<EventView> lastArticleSince(Instant since) {
		return this.delegate.lastArticleSince(since);
	}

}
