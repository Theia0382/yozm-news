package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.event.ArticleAssignment;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.EventView;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.tag.EvaluationReview;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 기사 하나의 상세: 기사 정보 · 평가 · 사건 소속 · 표현의 핵심 말. docs/specs/admin.md §3 · admin-R8.
 * <p>
 * 기사 정보는 {@link AdminArticleDetails} 가 맞붙이고(admin-R6), 없는(지운) 기사면 다른 조회를 부르지 않고 404 다. 평가는 {@code tag} 의
 * 평가 검수, 배정 · 핵심 말은 {@code event} 의 운영자 조회, 배정이 가리키는 사건의 지금 값은 {@code event} 의 사건 조회, 반영한 판정 원본은
 * {@code evaluation} 의 판정 원본 조회로 읽는다. 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다.
 */
@WebRole
@RestController
class AdminArticleController {

	private final AdminArticleDetails details;

	private final EvaluationReview evaluations;

	private final EventReview review;

	private final EventDirectory events;

	private final EventJudgementLog judgements;

	AdminArticleController(ArticleDirectory articles, FeedLabels labels, EvaluationReview evaluations,
			EventReview review, EventDirectory events, EventJudgementLog judgements) {
		this.details = new AdminArticleDetails(articles, labels);
		this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
		this.review = Objects.requireNonNull(review, "review");
		this.events = Objects.requireNonNull(events, "events");
		this.judgements = Objects.requireNonNull(judgements, "judgements");
	}

	@GetMapping("/api/admin/articles/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 기사다 — 그새 지운 기사 포함",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ArticleReviewResponse article(@Parameter(description = "기사 ID") @PathVariable long id) {
		ArticleId articleId = AdminIds.article(id);
		AdminArticleDetails.Detail article = this.details.detail(articleId)
			.orElseThrow(() -> AdminIds.unknownArticle(id));
		ArticleAssignment assignment = this.review.assignmentOf(articleId).orElse(null);
		return new ArticleReviewResponse(id, ArticleDetailResponse.of(article),
				this.evaluations.ofArticle(articleId).stream().map(ArticleEvaluationResponse::of).toList(),
				(assignment == null) ? null : assignment(assignment),
				this.review.keyTermsOf(articleId).stream().map(ArticleKeyTermsResponse::of).toList());
	}

	/** 배정에 사건의 지금 값과 반영한 판정 원본을 맞붙인다 — 조회 사이에 사라진 사건 · 지운 원본은 null 이다 */
	private ArticleAssignmentResponse assignment(ArticleAssignment assignment) {
		EventView event = this.events.find(assignment.event()).orElse(null);
		EventJudgementId judgementId = assignment.judgement();
		EventJudgementView judgement = (judgementId == null) ? null : this.judgements.find(judgementId).orElse(null);
		return ArticleAssignmentResponse.of(assignment, event, judgement);
	}

}
