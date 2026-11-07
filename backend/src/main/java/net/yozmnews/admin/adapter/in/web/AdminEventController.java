package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventCursor;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.EventSummary;
import net.yozmnews.event.EventView;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 사건 목록 · 사건 하나의 상세. docs/specs/admin.md §3 · admin-R7.
 * <p>
 * {@code event} 의 공개 조회를 불러 옮기고, 기사 정보는 {@link AdminArticles} 가 맞붙인다(admin-R6). 목록의 쪽 크기는 {@link #PAGE_SIZE}
 * 로 정해 부른다 — 질의로 받지 않는다. 기간의 범위는 공개 타입이 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다. 트랜잭션을 열지 않는다 — 여러
 * 공개 조회는 한 스냅숏이 아니다.
 */
@WebRole
@RestController
class AdminEventController {

	/** 목록 한 쪽의 사건 수 — {@link EventDirectory#MAX_PAGE_SIZE} 이하 */
	static final int PAGE_SIZE = 50;

	private final EventDirectory directory;

	private final EventReview review;

	private final AdminArticles articles;

	AdminEventController(EventDirectory directory, EventReview review, ArticleDirectory articles, FeedLabels labels) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.review = Objects.requireNonNull(review, "review");
		this.articles = new AdminArticles(articles, labels);
	}

	@GetMapping("/api/admin/events")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM + ", 또는 커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EventListResponse events(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "이어 받을 커서의 마지막 기사 시각(ISO-8601) — afterId 와 함께") @RequestParam(
					required = false) @Nullable String afterLastArticleAt,
			@Parameter(description = "이어 받을 커서의 사건 ID — afterLastArticleAt 과 함께") @RequestParam(
					required = false) @Nullable Long afterId) {
		EventPeriod period = AdminPeriods.events(from, to, zone);
		AdminCursor cursor = AdminCursor.of(afterLastArticleAt, afterId, "afterLastArticleAt", "사건");
		EventPage page = this.directory.list(period,
				(cursor == null) ? null : new EventCursor(cursor.at(), new EventId(cursor.id())), PAGE_SIZE);
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles.summaries(page.events()
			.stream()
			.map(EventSummary::representative)
			.<ArticleId>mapMulti((representative, sink) -> {
				if (representative != null) {
					sink.accept(representative);
				}
			})
			.toList());
		List<EventRowResponse> rows = page.events()
			.stream()
			.map(summary -> EventRowResponse.of(summary,
					(summary.representative() == null) ? null : summaries.get(summary.representative())))
			.toList();
		return EventListResponse.of(rows, (page.next() == null) ? null : EventCursorResponse.of(page.next()));
	}

	@GetMapping("/api/admin/events/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 사건이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EventDetailResponse event(
			@Parameter(description = "사건 ID — 합쳐진 사건이면 남는 사건을 낸다") @PathVariable long id) {
		EventId requested = AdminIds.event(id);
		EventView event = this.directory.find(requested).orElseThrow(() -> AdminIds.unknownEvent(id));
		List<EventArticleView> assigned = this.directory.articles(event.id());
		ArticleId representative = this.directory.representativesOfAll(List.of(event.id())).get(event.id());
		// 대표는 사건의 기사다 — 기사 정보 조회 한 번으로 함께 맞붙인다(그새 바뀐 배정이면 기사 정보가 null 이다)
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles
			.summaries(assigned.stream().map(EventArticleView::articleId).toList());
		return EventDetailResponse.of(id, event, (representative == null) ? null : representative.value(),
				(representative == null) ? null : summaries.get(representative),
				assigned.stream().map(view -> EventArticleResponse.of(view, summaries.get(view.articleId()))).toList(),
				this.review.mergedInto(event.id()).stream().map(MergedEventResponse::of).toList());
	}

}
