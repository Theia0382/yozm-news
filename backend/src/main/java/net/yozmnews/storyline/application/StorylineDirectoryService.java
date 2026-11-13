package net.yozmnews.storyline.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.EventStoryline;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import net.yozmnews.storyline.domain.big.BigBasis;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.storyline.domain.big.BigStorylineRule;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.ClosedMembers;
import net.yozmnews.storyline.domain.storyline.StateCriteria;
import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link StorylineDirectory} 의 구현 — docs/specs/storyline.md §4 · storyline-R19 · storyline-R21 ~ R23 · storyline-R33.
 * <p>
 * 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통). 큰 판정은 {@link #bigStorylines(EventPeriod, BigStorylineCriteria)} 하나를 공개 조회와
 * 미리보기가 함께 쓴다. 스토리라인의 사건은 닫은 사건 집합({@link ClosedMembers} — 직접 소속과 포함을 따라 내려간 하위의 직접 소속)이고, 스토리라인
 * 값 · 타임라인 · 사건의 스토리라인 · 기간의 스토리라인 · 기간 집계가 이 한 길을 쓴다. 닫은 사건 집합은 저장하지 않고 질의마다 관계와
 * 소속에서 만든다. 기사는 {@code event} 가 세고 여기서는 사건 ID 만 넘긴다.
 */
@Service
class StorylineDirectoryService implements StorylineDirectory {

	/** 타임라인의 순서 — 첫 기사 시각, 사건 ID */
	private static final Comparator<EventView> TIMELINE = Comparator.comparing(EventView::firstArticleAt)
		.thenComparingLong(event -> event.id().value());

	private final StorylineQueryRepository storylines;

	private final StorylineRepository memberships;

	private final RelationRepository relations;

	private final BigStorylineCriteriaRepository criteria;

	private final EventDirectory events;

	private final StateCriteria state;

	private final InstantSource clock;

	@Autowired
	StorylineDirectoryService(StorylineQueryRepository storylines, StorylineRepository memberships,
			RelationRepository relations, BigStorylineCriteriaRepository criteria, EventDirectory events,
			StateCriteria state) {
		this(storylines, memberships, relations, criteria, events, state, InstantSource.system());
	}

	StorylineDirectoryService(StorylineQueryRepository storylines, StorylineRepository memberships,
			RelationRepository relations, BigStorylineCriteriaRepository criteria, EventDirectory events,
			StateCriteria state, InstantSource clock) {
		this.storylines = storylines;
		this.memberships = memberships;
		this.relations = relations;
		this.criteria = criteria;
		this.events = events;
		this.state = state;
		this.clock = clock;
	}

	@Override
	public Optional<StorylineView> find(StorylineId id) {
		return this.storylines.find(id.value()).map(stored -> views(List.of(stored)).getFirst());
	}

	@Override
	public List<StorylineEvent> events(StorylineId id) {
		Optional<StoredStoryline> stored = this.storylines.find(id.value());
		if (stored.isEmpty()) {
			return List.of();
		}
		long storyline = stored.get().id();
		ClosedMembers closure = closure(List.of(storyline), graph());
		Set<Long> direct = closure.direct(storyline);
		// 합쳐졌거나 지워졌는데 아직 소속을 따르지 않은 사건은 사건 조회에 없다
		return this.events.views(ids(closure.closed(storyline)))
			.values()
			.stream()
			.sorted(TIMELINE)
			.map(event -> new StorylineEvent(event, direct.contains(event.id().value())))
			.toList();
	}

	@Override
	public List<EventStoryline> storylinesOf(EventId event) {
		Optional<EventView> survivor = this.events.find(event);
		if (survivor.isEmpty()) {
			return List.of();
		}
		RelationGraph graph = graph();
		Set<Long> direct = new TreeSet<>();
		this.storylines.having(List.of(survivor.get().id().value())).forEach(stored -> direct.add(stored.id()));
		List<StoredStoryline> rows = this.storylines.byIds(withAncestors(direct, graph));
		List<StorylineView> views = views(rows, graph);
		List<EventStoryline> found = new ArrayList<>();
		for (StorylineView view : views) {
			found.add(new EventStoryline(view, direct.contains(view.id().value())));
		}
		return found;
	}

	@Override
	public StorylinePage list(EventPeriod period, @Nullable StorylineCursor after, int size) {
		Objects.requireNonNull(period, "period");
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("스토리라인 목록은 한 쪽에 1 ~ " + MAX_PAGE_SIZE + "건이다: " + size);
		}
		RelationGraph graph = graph();
		// 한 건 더 읽어 다음 쪽이 있는지 안다. 순서 · 커서는 직접 소속의 마지막 기사 시각(저장된 값)이다
		List<StoredStoryline> rows = this.storylines.pageOf(storylinesIn(period, graph),
				after == null ? null : after.lastArticleAt(), after == null ? null : after.id().value(), size + 1);
		List<StoredStoryline> page = rows.size() > size ? rows.subList(0, size) : rows;
		if (page.isEmpty()) {
			return new StorylinePage(List.of(), null);
		}
		ClosedMembers closure = closure(page.stream().map(StoredStoryline::id).toList(), graph);
		Map<Long, EventTally> tallies = tallied(page, closure, period);
		BigBasis basis = basis(period);
		BigStorylineCriteria current = currentCriteria();
		List<StorylineView> views = views(page, graph, closure);
		List<StorylineSummary> summaries = new ArrayList<>();
		for (StorylineView view : views) {
			EventTally tally = tallies.get(view.id().value());
			summaries.add(new StorylineSummary(view, tally, BigStorylineRule.intensity(tally, basis),
					BigStorylineRule.isBig(tally, basis, current)));
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

	@Override
	public List<StorylineRelationView> relationsOf(StorylineId id) {
		// 합쳐진 스토리라인이면 남는 쪽을 따른 뒤 읽는다 — 합칠 때 관계를 남는 쪽으로 옮겼다(storyline-R32)
		return this.storylines.find(id.value())
			.map(stored -> this.relations.of(stored.id()).stream().map(RelationViews::of).toList())
			.orElse(List.of());
	}

	/** 기간의 큰 스토리라인을 주어진 기준으로 판정한다 — 공개 조회(지금 기준)와 미리보기(제안 기준)가 함께 쓴다 */
	List<BigStoryline> bigStorylines(EventPeriod period, BigStorylineCriteria criteria) {
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(criteria, "criteria");
		RelationGraph graph = graph();
		List<StoredStoryline> rows = this.storylines.byIds(storylinesIn(period, graph));
		if (rows.isEmpty()) {
			return List.of();
		}
		ClosedMembers closure = closure(rows.stream().map(StoredStoryline::id).toList(), graph);
		Map<Long, EventTally> tallies = tallied(rows, closure, period);
		BigBasis basis = basis(period);
		List<BigStoryline> big = new ArrayList<>();
		for (StorylineView view : views(rows, graph, closure)) {
			EventTally tally = tallies.get(view.id().value());
			if (BigStorylineRule.isBig(tally, basis, criteria)) {
				big.add(new BigStoryline(view, tally, BigStorylineRule.intensity(tally, basis)));
			}
		}
		big.sort(BigStorylineRule.ORDER);
		return List.copyOf(big);
	}

	/** 큰 판정의 분모 — 기간 전체 집계의 소스 수는 {@code event} 가 센다(storyline-R19) */
	private BigBasis basis(EventPeriod period) {
		return BigBasis.of(this.events.total(period), period);
	}

	/**
	 * 지금 큰 판정 기준.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	BigStorylineCriteria currentCriteria() {
		return this.criteria.current().orElseThrow(() -> new IllegalStateException("큰 판정 기준의 처음 값이 없다"));
	}

	/**
	 * 기간의 스토리라인(storyline-R22 · R33) — 조회 기간 안 기사가 있는 사건이 직접 든 스토리라인과 그 상위 전부. 하위에만 기간 안 사건이
	 * 있어도 상위가 든다
	 */
	private Set<Long> storylinesIn(EventPeriod period, RelationGraph graph) {
		List<Long> inPeriod = this.events.articleCountsIn(period).keySet().stream().map(EventId::value).toList();
		Set<Long> direct = new TreeSet<>();
		this.storylines.having(inPeriod).forEach(stored -> direct.add(stored.id()));
		return withAncestors(direct, graph);
	}

	private static Set<Long> withAncestors(Set<Long> storylineIds, RelationGraph graph) {
		Set<Long> all = new TreeSet<>(storylineIds);
		storylineIds.forEach(id -> all.addAll(graph.ancestors(id)));
		return all;
	}

	/** 스토리라인마다 기간 집계 — 닫은 사건 집합을 묶음으로 넘기고 {@code event} 가 센다(storyline-R19 · R33) */
	private Map<Long, EventTally> tallied(List<StoredStoryline> rows, ClosedMembers closure, EventPeriod period) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<EventId>> groups = new LinkedHashMap<>();
		for (StoredStoryline row : rows) {
			groups.put(row.id(), ids(closure.closed(row.id())));
		}
		return this.events.tallies(groups, period);
	}

	private List<StorylineView> views(List<StoredStoryline> rows) {
		return views(rows, graph());
	}

	private List<StorylineView> views(List<StoredStoryline> rows, RelationGraph graph) {
		return views(rows, graph, closure(rows.stream().map(StoredStoryline::id).toList(), graph));
	}

	/**
	 * 스토리라인 값(storyline-R33) — 하위가 없으면 저장된 표현의 값, 있으면 닫은 사건 집합의 사건으로 센 값이다. 상태는 그 마지막 기사 시각으로
	 * 정한다. 대표 사건은 직접 소속의 것이다
	 */
	private List<StorylineView> views(List<StoredStoryline> rows, RelationGraph graph, ClosedMembers closure) {
		Instant now = this.clock.instant();
		Set<Long> inherited = new LinkedHashSet<>();
		for (StoredStoryline row : rows) {
			if (closure.hasChildren(row.id())) {
				inherited.addAll(closure.closed(row.id()));
			}
		}
		Map<EventId, EventView> events = this.events.views(ids(inherited));
		List<StorylineView> views = new ArrayList<>();
		for (StoredStoryline row : rows) {
			StorylineId parent = graph.parentOf(row.id()).map(StorylineId::new).orElse(null);
			int eventCount = row.eventCount();
			int articleCount = row.articleCount();
			Instant first = row.firstArticleAt();
			Instant last = row.lastArticleAt();
			if (closure.hasChildren(row.id())) {
				Set<Long> closed = closure.closed(row.id());
				List<EventView> counted = closed.stream()
					.map(event -> events.get(new EventId(event)))
					.filter(Objects::nonNull)
					.toList();
				eventCount = closed.size();
				if (!counted.isEmpty()) {
					articleCount = counted.stream().mapToInt(EventView::articleCount).sum();
					first = counted.stream().map(EventView::firstArticleAt).min(Comparator.naturalOrder()).orElseThrow();
					last = counted.stream().map(EventView::lastArticleAt).max(Comparator.naturalOrder()).orElseThrow();
				}
			}
			views.add(new StorylineView(new StorylineId(row.id()), this.state.stateOf(last, now), eventCount,
					articleCount, first, last, new EventId(row.representativeEventId()), row.createdAt(), parent));
		}
		return views;
	}

	private RelationGraph graph() {
		return new RelationGraph(this.relations.all());
	}

	/** 스토리라인들의 닫은 사건 집합 — 저마다 자기와 모든 하위의 직접 소속을 한 번에 읽어 합친다 */
	private ClosedMembers closure(Collection<Long> storylineIds, RelationGraph graph) {
		Map<Long, Set<Long>> families = ClosedMembers.families(storylineIds, graph);
		return new ClosedMembers(families, this.memberships.members(ClosedMembers.everyone(families)));
	}

	private static List<EventId> ids(Collection<Long> values) {
		return values.stream().map(EventId::new).toList();
	}

}
