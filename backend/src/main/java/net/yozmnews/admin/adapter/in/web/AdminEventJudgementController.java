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
import net.yozmnews.evaluation.EventJudgementCursor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementPage;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgementPeriod;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 사건 소속 판정 기록: 기간의 판정 원본과 그 쓰임, 지금 사건 배정 기준. docs/specs/admin.md §3 · admin-R7.
 * <p>
 * {@code evaluation} 의 판정 원본 조회와 {@code event} 의 쓰임 조회를 원본 ID 로 맞붙이고, 판정할 기사의 정보는 {@link AdminArticles} 가
 * 맞붙인다(admin-R6). 쪽 크기는 {@link #PAGE_SIZE} 로 정해 부른다 — 질의로 받지 않는다. 트랜잭션을 열지 않는다 — 원본 · 쓰임 · 기사 정보는
 * 한 스냅숏이 아니다.
 */
@WebRole
@RestController
class AdminEventJudgementController {

	/** 한 쪽의 판정 기록 수 — {@link EventJudgementLog#MAX_PAGE_SIZE} 이하 */
	static final int PAGE_SIZE = 50;

	private final EventJudgementLog log;

	private final EventReview review;

	private final AdminArticles articles;

	AdminEventJudgementController(EventJudgementLog log, EventReview review, ArticleDirectory articles,
			FeedLabels labels) {
		this.log = Objects.requireNonNull(log, "log");
		this.review = Objects.requireNonNull(review, "review");
		this.articles = new AdminArticles(articles, labels);
	}

	@GetMapping("/api/admin/event-judgements")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM + ", 또는 커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EventJudgementListResponse judgements(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "이어 받을 커서의 판정 시각(ISO-8601) — afterId 와 함께") @RequestParam(
					required = false) @Nullable String afterJudgedAt,
			@Parameter(description = "이어 받을 커서의 판정 원본 ID — afterJudgedAt 과 함께") @RequestParam(
					required = false) @Nullable Long afterId) {
		JudgementPeriod period = AdminPeriods.judgements(from, to, zone);
		AdminCursor cursor = AdminCursor.of(afterJudgedAt, afterId, "afterJudgedAt", "판정 원본");
		EventJudgementPage page = this.log.list(period,
				(cursor == null) ? null : new EventJudgementCursor(cursor.at(), new EventJudgementId(cursor.id())),
				PAGE_SIZE);
		Map<EventJudgementId, JudgementUse> uses = this.review
			.judgementUses(page.judgements().stream().map(EventJudgementView::id).toList());
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles
			.summaries(page.judgements().stream().map(EventJudgementView::articleId).distinct().toList());
		List<EventJudgementRowResponse> rows = page.judgements()
			.stream()
			.map(view -> EventJudgementRowResponse.of(view, uses.get(view.id()), summaries.get(view.articleId())))
			.toList();
		return EventJudgementListResponse.of(rows,
				(page.next() == null) ? null : JudgementCursorResponse.of(page.next()),
				AssignmentCriteriaResponse.of(this.review.assignmentCriteria()));
	}

}
