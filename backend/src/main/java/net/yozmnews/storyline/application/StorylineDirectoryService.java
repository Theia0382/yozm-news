package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.storyline.domain.big.BigStorylineRule;
import net.yozmnews.storyline.domain.storyline.StateCriteria;
import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link StorylineDirectory} 의 구현 — docs/specs/storyline.md §4 · storyline-R19 · storyline-R21 ~ R23.
 * <p>
 * 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통). 큰 판정은 {@link #bigStorylines(EventPeriod, BigStorylineCriteria)} 하나를 공개 조회와
 * 미리보기가 함께 쓰고, 기간 집계는 {@link #tallied} 하나를 목록과 큰 판정이 함께 쓴다 — 기사는 {@code event} 가 세고 여기서는 구성 사건 ID 만
 * 넘긴다.
 */
@Service
class StorylineDirectoryService implements StorylineDirectory {

	/** 타임라인의 순서 — 첫 기사 시각, 사건 ID */
	private static final Comparator<EventView> TIMELINE = Comparator.comparing(EventView::firstArticleAt)
		.thenComparingLong(event -> event.id().value());

	private final StorylineQueryRepository storylines;

	private final StorylineRepository memberships;

	private final BigStorylineCriteriaRepository criteria;

	private final EventDirectory events;

	private final StateCriteria state;

	private final InstantSource clock;

	@Autowired
	StorylineDirectoryService(StorylineQueryRepository storylines, StorylineRepository memberships,
			BigStorylineCriteriaRepository criteria, EventDirectory events, StateCriteria state) {
		this(storylines, memberships, criteria, events, state, InstantSource.system());
	}

	StorylineDirectoryService(StorylineQueryRepository storylines, StorylineRepository memberships,
			BigStorylineCriteriaRepository criteria, EventDirectory events, StateCriteria state, InstantSource clock) {
		this.storylines = storylines;
		this.memberships = memberships;
		this.criteria = criteria;
		this.events = events;
		this.state = state;
		this.clock = clock;
	}

	@Override
	public Optional<StorylineView> find(StorylineId id) {
		Instant now = this.clock.instant();
		return this.storylines.find(id.value()).map(stored -> view(stored, now));
	}

	@Override
	public List<EventView> events(StorylineId id) {
		Optional<StoredStoryline> stored = this.storylines.find(id.value());
		if (stored.isEmpty()) {
			return List.of();
		}
		long storyline = stored.get().id();
		List<EventId> members = this.memberships.members(List.of(storyline))
			.getOrDefault(storyline, List.of())
			.stream()
			.map(EventId::new)
			.toList();
		// 합쳐졌거나 지워졌는데 아직 소속을 따르지 않은 사건은 사건 조회에 없다
		return this.events.views(members).values().stream().sorted(TIMELINE).toList();
	}

	@Override
	public List<StorylineView> storylinesOf(EventId event) {
		Optional<EventView> survivor = this.events.find(event);
		if (survivor.isEmpty()) {
			return List.of();
		}
		Instant now = this.clock.instant();
		return this.storylines.having(List.of(survivor.get().id().value()))
			.stream()
			.map(stored -> view(stored, now))
			.toList();
	}

	@Override
	public StorylinePage list(EventPeriod period, @Nullable StorylineCursor after, int size) {
		Objects.requireNonNull(period, "period");
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("스토리라인 목록은 한 쪽에 1 ~ " + MAX_PAGE_SIZE + "건이다: " + size);
		}
		// 한 건 더 읽어 다음 쪽이 있는지 안다
		List<StoredStoryline> rows = this.storylines.page(eventsIn(period), after == null ? null : after.lastArticleAt(),
				after == null ? null : after.id().value(), size + 1);
		List<StoredStoryline> page = rows.size() > size ? rows.subList(0, size) : rows;
		if (page.isEmpty()) {
			return new StorylinePage(List.of(), null);
		}
		Map<Long, EventTally> tallies = tallied(page, period);
		BigStorylineCriteria current = currentCriteria();
		Instant now = this.clock.instant();
		List<StorylineSummary> summaries = new ArrayList<>();
		for (StoredStoryline stored : page) {
			EventTally tally = tallies.get(stored.id());
			summaries.add(new StorylineSummary(view(stored, now), tally, BigStorylineRule.isBig(tally, current)));
		}
		StoredStoryline last = page.getLast();
		StorylineCursor next = rows.size() > size ? new StorylineCursor(last.lastArticleAt(), new StorylineId(last.id()))
				: null;
		return new StorylinePage(summaries, next);
	}

	@Override
	public List<BigStoryline> bigStorylines(EventPeriod period) {
		return bigStorylines(period, currentCriteria());
	}

	@Override
	public UnlinkedEvents unlinked(EventPeriod period) {
		Objects.requireNonNull(period, "period");
		Map<EventId, Integer> counts = this.events.articleCountsIn(period);
		if (counts.isEmpty()) {
			return new UnlinkedEvents(0, 0);
		}
		Set<Long> affiliated = this.memberships.affiliated(counts.keySet().stream().map(EventId::value).toList());
		int events = 0;
		int articles = 0;
		for (Map.Entry<EventId, Integer> entry : counts.entrySet()) {
			if (!affiliated.contains(entry.getKey().value())) {
				events++;
				articles += entry.getValue();
			}
		}
		return new UnlinkedEvents(events, articles);
	}

	/** 기간의 큰 스토리라인을 주어진 기준으로 판정한다 — 공개 조회(지금 기준)와 미리보기(제안 기준)가 함께 쓴다 */
	List<BigStoryline> bigStorylines(EventPeriod period, BigStorylineCriteria criteria) {
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(criteria, "criteria");
		List<StoredStoryline> rows = this.storylines.having(eventsIn(period));
		Map<Long, EventTally> tallies = tallied(rows, period);
		Instant now = this.clock.instant();
		List<BigStoryline> big = new ArrayList<>();
		for (StoredStoryline stored : rows) {
			EventTally tally = tallies.get(stored.id());
			if (BigStorylineRule.isBig(tally, criteria)) {
				big.add(new BigStoryline(view(stored, now), tally));
			}
		}
		big.sort(BigStorylineRule.ORDER);
		return List.copyOf(big);
	}

	/**
	 * 지금 큰 판정 기준.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	BigStorylineCriteria currentCriteria() {
		return this.criteria.current().orElseThrow(() -> new IllegalStateException("큰 판정 기준의 처음 값이 없다"));
	}

	/** 조회 기간 안 기사가 있는 사건 — 기간의 스토리라인을 고르는 재료(storyline-R22) */
	private List<Long> eventsIn(EventPeriod period) {
		return this.events.articleCountsIn(period).keySet().stream().map(EventId::value).toList();
	}

	/** 스토리라인마다 기간 집계 — 구성 사건 ID 를 묶음으로 넘기고 {@code event} 가 센다(storyline-R19) */
	private Map<Long, EventTally> tallied(List<StoredStoryline> rows, EventPeriod period) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = rows.stream().map(StoredStoryline::id).toList();
		Map<Long, List<Long>> members = this.memberships.members(ids);
		Map<Long, List<EventId>> groups = new LinkedHashMap<>();
		for (Long id : ids) {
			groups.put(id, members.getOrDefault(id, List.of()).stream().map(EventId::new).toList());
		}
		return this.events.tallies(groups, period);
	}

	private StorylineView view(StoredStoryline stored, Instant now) {
		return new StorylineView(new StorylineId(stored.id()), this.state.stateOf(stored.lastArticleAt(), now),
				stored.eventCount(), stored.articleCount(), stored.firstArticleAt(), stored.lastArticleAt(),
				new EventId(stored.representativeEventId()), stored.createdAt());
	}

}
