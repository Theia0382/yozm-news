package net.yozmnews.event.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventCursor;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventSummary;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import net.yozmnews.event.domain.bigevent.BigEventCriteriaRepository;
import net.yozmnews.event.domain.bigevent.BigEventRule;
import net.yozmnews.event.domain.event.EventQueryRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link EventDirectory} 의 구현 — docs/specs/event.md §4 · event-R14 · event-R15 · event-R20 · event-R24.
 * <p>
 * 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통). 큰 사건 판정은 {@link #bigEvents(EventPeriod, BigEventCriteria)} 하나를 공개 조회와
 * 미리보기가 함께 쓴다.
 */
@Service
class EventDirectoryService implements EventDirectory {

	private static final EventTally EMPTY = new EventTally(0, 0, null);

	private final EventQueryRepository events;

	private final EventTallyReader tallies;

	private final BigEventCriteriaRepository criteria;

	EventDirectoryService(EventQueryRepository events, EventTallyReader tallies, BigEventCriteriaRepository criteria) {
		this.events = events;
		this.tallies = tallies;
		this.criteria = criteria;
	}

	@Override
	public Optional<EventView> find(EventId id) {
		return this.events.find(id.value());
	}

	@Override
	public List<EventArticleView> articles(EventId id) {
		return this.events.articles(id.value());
	}

	@Override
	public Map<ArticleId, EventId> eventsOf(Collection<ArticleId> articleIds) {
		Map<ArticleId, EventId> result = new LinkedHashMap<>();
		this.events.eventsOf(articleIds).forEach((article, event) -> result.put(article, new EventId(event)));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public Map<EventId, ArticleId> representatives(Map<EventId, ? extends Collection<ArticleId>> among) {
		Map<Long, Collection<ArticleId>> byId = new LinkedHashMap<>();
		among.forEach((event, articles) -> byId.put(event.value(), articles));
		Map<EventId, ArticleId> result = new LinkedHashMap<>();
		this.events.representatives(byId).forEach((event, article) -> result.put(new EventId(event), article));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public Map<EventId, ArticleId> representativesOfAll(Collection<EventId> events) {
		if (events.isEmpty()) {
			return Map.of();
		}
		Map<EventId, ArticleId> result = new LinkedHashMap<>();
		this.events.representativesOfAll(events.stream().map(EventId::value).distinct().toList())
			.forEach((event, article) -> result.put(new EventId(event), article));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public List<BigEvent> bigEvents(EventPeriod period) {
		return bigEvents(period, currentCriteria());
	}

	@Override
	public EventPage list(EventPeriod period, @Nullable EventCursor after, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("사건 목록은 한 쪽에 1 ~ " + MAX_PAGE_SIZE + "건이다: " + size);
		}
		// 한 건 더 읽어 다음 쪽이 있는지 안다
		List<EventView> rows = this.events.page(period.from(), period.to(),
				after == null ? null : after.lastArticleAt(), after == null ? null : after.id().value(), size + 1);
		List<EventView> page = rows.size() > size ? rows.subList(0, size) : rows;
		if (page.isEmpty()) {
			return new EventPage(List.of(), null);
		}
		List<Long> ids = page.stream().map(event -> event.id().value()).toList();
		Map<Long, EventTally> tallied = this.tallies.read(this.events.membersIn(ids, period.from(), period.to()));
		Map<Long, ArticleId> representatives = this.events.representativesOfAll(ids);
		BigEventCriteria current = currentCriteria();
		List<EventSummary> summaries = new ArrayList<>();
		for (EventView event : page) {
			EventTally tally = tallied.getOrDefault(event.id().value(), EMPTY);
			summaries.add(new EventSummary(event, tally, BigEventRule.isBig(tally, current),
					representatives.get(event.id().value())));
		}
		EventView last = page.getLast();
		EventCursor next = rows.size() > size ? new EventCursor(last.lastArticleAt(), last.id()) : null;
		return new EventPage(summaries, next);
	}

	@Override
	public Map<EventId, EventProfile> profiles(Collection<EventId> ids, int keyTerms) {
		requireKeyTerms(keyTerms);
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<EventId, EventProfile> result = new LinkedHashMap<>();
		this.events.profiles(ids.stream().map(EventId::value).distinct().toList(), keyTerms)
			.forEach((event, profile) -> result.put(new EventId(event), profile));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public List<EventNeighbor> neighbors(EventId id, Instant from, Instant to, double minCosine, int keyTerms,
			int limit) {
		Objects.requireNonNull(id, "id");
		requireRange(from, to);
		requireKeyTerms(keyTerms);
		if (limit < 1 || limit > MAX_NEIGHBORS) {
			throw new IllegalArgumentException("이웃 사건은 한 번에 1 ~ " + MAX_NEIGHBORS + "건이다: " + limit);
		}
		return this.events.neighbors(id.value(), from, to, minCosine, keyTerms, limit);
	}

	@Override
	public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
		requireRange(from, to);
		List<String> distinct = terms.stream().distinct().toList();
		if (distinct.size() > MAX_TERMS) {
			throw new IllegalArgumentException("말 수는 한 번에 " + MAX_TERMS + "개까지 묻는다: " + distinct.size());
		}
		return this.events.termCounts(distinct, from, to);
	}

	@Override
	public List<EventView> lastArticleSince(Instant since) {
		return this.events.lastArticleSince(Objects.requireNonNull(since, "since"));
	}

	private static void requireRange(Instant from, Instant to) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("구간의 시작이 끝보다 늦다: " + from + " > " + to);
		}
	}

	private static void requireKeyTerms(int keyTerms) {
		if (keyTerms < 1 || keyTerms > MAX_KEY_TERMS) {
			throw new IllegalArgumentException("핵심 말 수는 1 ~ " + MAX_KEY_TERMS + "개다: " + keyTerms);
		}
	}

	/** 기간 안의 큰 사건을 주어진 기준으로 판정한다 — 공개 조회(지금 기준)와 미리보기(제안 기준)가 함께 쓴다 */
	List<BigEvent> bigEvents(EventPeriod period, BigEventCriteria criteria) {
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(criteria, "criteria");
		Map<Long, List<ArticleId>> members = this.events.membersIn(period.from(), period.to());
		Map<Long, EventTally> tallied = this.tallies.read(members);
		List<BigEvent> big = new ArrayList<>();
		tallied.forEach((event, tally) -> {
			if (BigEventRule.isBig(tally, criteria)) {
				big.add(new BigEvent(new EventId(event), members.get(event), tally));
			}
		});
		big.sort(BigEventRule.ORDER);
		return List.copyOf(big);
	}

	/**
	 * 지금 큰 사건 기준.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	BigEventCriteria currentCriteria() {
		return this.criteria.current().orElseThrow(() -> new IllegalStateException("큰 사건 기준의 처음 값이 없다"));
	}

}
