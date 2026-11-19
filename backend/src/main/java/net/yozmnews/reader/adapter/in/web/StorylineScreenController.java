package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.digest.ReceivedArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 스토리라인 — 목록 기간의 목록 · 스토리라인 하나의 타임라인 · ID 여럿의 카드. docs/specs/reader.md §3 · reader-R1 ~ R7.
 * <p>
 * {@code storyline} · {@code event} · {@code article} · {@code digest} 의 공개 조회만 부르고 응답 모양을 정한다. 세 처리기 모두 세션이 필요하고
 * 키 관문 뒤다(키 없이 여는 표시 · 요청 방어 표시가 없다). 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다.
 */
@WebRole
@RestController
class StorylineScreenController {

	/** 목록 한 쪽의 스토리라인 수 — {@link StorylineDirectory#MAX_PAGE_SIZE} 이하. 측정 전 목표 */
	static final int PAGE_SIZE = 30;

	private final StorylineDirectory storylines;

	private final StorylineTitles titles;

	private final ReceivedMarks received;

	private final InstantSource clock;

	StorylineScreenController(StorylineDirectory storylines, EventDirectory events, ArticleDirectory articles,
			ReceivedArticles received, InstantSource clock) {
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.titles = new StorylineTitles(events, new ScreenArticles(articles));
		this.received = new ReceivedMarks(events, received);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@GetMapping("/api/storylines")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "기간 끝 · 커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylinePageResponse storylinePage(
			@Parameter(description = "목록 기간 — 기간 끝에서 거슬러 잰 24시간 · 7일 · 30일") @RequestParam(
					defaultValue = "WEEK") StorylineWindow window,
			@Parameter(description = "기간 끝(ISO-8601, 제외) — 없으면 지금. 다음 쪽은 앞 응답의 until 을 넘긴다") @RequestParam(
					required = false) @Nullable String until,
			@Parameter(description = "다음 쪽 커서의 마지막 기사 시각(ISO-8601) — afterId 와 함께") @RequestParam(
					required = false) @Nullable String afterLastArticleAt,
			@Parameter(description = "다음 쪽 커서의 스토리라인 ID — afterLastArticleAt 과 함께") @RequestParam(
					required = false) @Nullable Long afterId) {
		EventPeriod period = ScreenRequests.period(window, until, this.clock.instant());
		StorylinePage page = this.storylines.list(period, ScreenRequests.cursor(afterLastArticleAt, afterId),
				PAGE_SIZE);
		Map<EventId, NewsArticleResponse> titles = this.titles
			.of(page.storylines().stream().map(summary -> summary.storyline().representativeEvent()).toList());
		List<StorylineCardResponse> cards = page.storylines()
			.stream()
			.map(summary -> card(summary, titles))
			.toList();
		return new StorylinePageResponse(window, period.from(), period.to(), cards,
				(page.next() == null) ? null : StorylinePageCursorResponse.of(page.next()));
	}

	@GetMapping("/api/storylines/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 스토리라인이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineTimelineResponse storylineTimeline(UserId user,
			@Parameter(description = "스토리라인 ID — 합쳐진 스토리라인이면 남는 스토리라인을 낸다") @PathVariable long id) {
		StorylineView view = this.storylines.find(ScreenRequests.storyline(id))
			.orElseThrow(() -> ScreenRequests.unknownStoryline(id));
		List<StorylineEvent> timeline = this.storylines.events(view.id());
		Relations relations = Relations.of(view, this.storylines.relationsOf(view.id()));
		Map<StorylineId, StorylineView> related = this.storylines.findAll(relations.all());
		// 대표 기사는 한 응답에 한 번 — 카드 · 타임라인 사건 · 상위와 관계 상대의 대표 사건을 모은다
		Map<EventId, NewsArticleResponse> titles = this.titles.of(Stream
			.concat(Stream.concat(Stream.of(view.representativeEvent()),
					timeline.stream().map(event -> event.event().id())),
					related.values().stream().map(StorylineView::representativeEvent))
			.toList());
		Map<EventId, LocalDate> marks = this.received.of(user,
				timeline.stream().map(event -> event.event().id()).toList());
		List<TimelineEventResponse> events = timeline.stream()
			.map(event -> new TimelineEventResponse(event.event().id().value(), event.direct(),
					event.event().articleCount(), event.event().firstArticleAt(), event.event().lastArticleAt(),
					titles.get(event.event().id()), marks.get(event.event().id())))
			.toList();
		StorylineRefResponse parent = (view.parent() == null) ? null
				: refs(List.of(view.parent()), related, titles).stream().findFirst().orElse(null);
		return new StorylineTimelineResponse(StorylineCardResponse.of(view, titles.get(view.representativeEvent()), null),
				parent, refs(relations.children(), related, titles), refs(relations.preceding(), related, titles),
				refs(relations.following(), related, titles), events);
	}

	@GetMapping("/api/storyline-cards")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "ids 가 비었거나 너무 많거나 1 미만 ID 가 있다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineCardsResponse storylineCards(
			@Parameter(description = "스토리라인 ID — 쉼표로 이은 1 ~ 100개(같은 ID 포함)") @RequestParam List<Long> ids) {
		List<StorylineId> requested = ScreenRequests.ids(ids);
		Map<StorylineId, StorylineView> found = this.storylines.findAll(requested);
		Map<EventId, NewsArticleResponse> titles = this.titles
			.of(found.values().stream().map(StorylineView::representativeEvent).toList());
		List<StorylineCardEntryResponse> cards = new ArrayList<>();
		for (StorylineId id : requested) {
			StorylineView view = found.get(id);
			if (view != null) {
				cards.add(new StorylineCardEntryResponse(id.value(),
						StorylineCardResponse.of(view, titles.get(view.representativeEvent()), null)));
			}
		}
		return new StorylineCardsResponse(cards);
	}

	private static StorylineCardResponse card(StorylineSummary summary, Map<EventId, NewsArticleResponse> titles) {
		StorylineView view = summary.storyline();
		return StorylineCardResponse.of(view, titles.get(view.representativeEvent()),
				new StorylinePeriodResponse(summary.tally().articles(), summary.big()));
	}

	/** 관계 상대 — findAll 이 낸 값(합쳐졌으면 남는 쪽)으로, 그새 없어진 상대는 빼고 같은 ID 는 한 번 */
	private static List<StorylineRefResponse> refs(List<StorylineId> ids, Map<StorylineId, StorylineView> related,
			Map<EventId, NewsArticleResponse> titles) {
		Set<Long> seen = new LinkedHashSet<>();
		List<StorylineRefResponse> refs = new ArrayList<>();
		for (StorylineId id : ids) {
			StorylineView view = related.get(id);
			if (view != null && seen.add(view.id().value())) {
				NewsArticleResponse title = titles.get(view.representativeEvent());
				refs.add(new StorylineRefResponse(view.id().value(), (title == null) ? null : title.title(),
						view.state()));
			}
		}
		return refs;
	}

	/**
	 * 보고 있는 스토리라인 S 기준의 관계 나누기 — reader-R4. 포함의 S 가 뒤인 고리(상위)는 담지 않는다 — 상위는 {@link StorylineView#parent}
	 * 하나로 정한다.
	 */
	private record Relations(@Nullable StorylineId parent, List<StorylineId> children, List<StorylineId> preceding,
			List<StorylineId> following) {

		static Relations of(StorylineView self, List<StorylineRelationView> relations) {
			List<StorylineId> children = new ArrayList<>();
			List<StorylineId> preceding = new ArrayList<>();
			List<StorylineId> following = new ArrayList<>();
			for (StorylineRelationView relation : relations) {
				boolean source = relation.source().equals(self.id());
				// switch 식 — 관계 종류가 늘면 컴파일 오류가 난다. 포함의 S 가 뒤인 고리(상위)는 담지 않는다
				Optional<List<StorylineId>> into = switch (relation.kind()) {
					case CONTAINS -> source ? Optional.of(children) : Optional.empty();
					case FOLLOWS -> Optional.of(source ? following : preceding);
				};
				into.ifPresent(list -> list.add(source ? relation.target() : relation.source()));
			}
			return new Relations(self.parent(), children, preceding, following);
		}

		/** findAll 로 한 번에 읽을 ID — 상위와 관계 상대 */
		Collection<StorylineId> all() {
			Set<StorylineId> all = new LinkedHashSet<>();
			if (this.parent != null) {
				all.add(this.parent);
			}
			all.addAll(this.children);
			all.addAll(this.preceding);
			all.addAll(this.following);
			return all;
		}

	}

}
