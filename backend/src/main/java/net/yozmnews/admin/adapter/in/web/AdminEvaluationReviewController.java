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
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.tag.EvaluationCursor;
import net.yozmnews.tag.EvaluationFilter;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.EvaluationListResult;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.EvaluationReview;
import net.yozmnews.tag.EvaluationRow;
import net.yozmnews.tag.EvaluationSampleResult;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 평가 검수: 버전 · 기간의 평가 목록(거르기 · 커서), 표본, 평가 하나의 상세. docs/specs/admin.md §3.
 * <p>
 * {@code tag} 의 공개 운영 API 를 불러 옮기고, 기사 정보는 {@link AdminArticles} 가 맞붙인다(admin-R6). 범위 · 없는 태그의 판정은 공개
 * API 가 하고 컨트롤러는 그 결과를 400 · 404 로 옮긴다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminEvaluationReviewController {

	/** 목록 한 번의 기본 쪽 크기 */
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final EvaluationReview review;

	private final AdminArticles articles;

	AdminEvaluationReviewController(EvaluationReview review, ArticleDirectory articles, FeedLabels labels) {
		this.review = Objects.requireNonNull(review, "review");
		this.articles = new AdminArticles(articles, labels);
	}

	@GetMapping("/api/admin/evaluations")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM
			+ ", 거르기 · 커서 · 쪽 크기가 틀렸거나 그 태그 체계 버전에 없는 태그다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 태그 체계 버전이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EvaluationListResponse evaluations(@Parameter(description = "태그 체계 버전 ID") @RequestParam long version,
			@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "거를 태그의 종류 — tag 와 함께") @RequestParam(required = false) @Nullable TagKind tagKind,
			@Parameter(description = "거를 태그의 키 — 주제는 대분류 키 · 중분류 전체 키") @RequestParam(
					required = false) @Nullable String tag,
			@Parameter(description = "거를 주제 판정") @RequestParam(required = false) @Nullable TopicStatus topicStatus,
			@Parameter(description = "이어 받을 커서의 평가 시각(ISO-8601) — afterId 와 함께") @RequestParam(
					required = false) @Nullable String afterEvaluatedAt,
			@Parameter(description = "이어 받을 커서의 평가 ID — afterEvaluatedAt 과 함께") @RequestParam(
					required = false) @Nullable Long afterId,
			@Parameter(description = "한 번에 받을 수 — 없으면 " + DEFAULT_PAGE_SIZE) @RequestParam(required = false) @Nullable Integer limit) {
		TagSetId id = AdminIds.tagSet(version);
		EvaluationPeriod period = AdminPeriods.of(from, to, zone);
		EvaluationFilter filter = new EvaluationFilter(tag(tagKind, tag), topicStatus);
		EvaluationCursor after = cursor(afterEvaluatedAt, afterId);
		return switch (this.review.list(id, period, filter, after, (limit != null) ? limit : DEFAULT_PAGE_SIZE)) {
			case EvaluationListResult.Listed listed ->
				EvaluationListResponse.of(listed.listing(), rows(listed.listing().rows()));
			case EvaluationListResult.UnknownVersion unknown -> throw AdminIds.unknownTagSet(version);
			case EvaluationListResult.UnknownTag unknown -> throw badRequest(
					"그 태그 체계 버전에 없는 태그다: " + unknown.tag().kind() + " " + unknown.tag().key());
			case EvaluationListResult.LimitOutOfRange outOfRange ->
				throw badRequest("한 번에 " + EvaluationReview.MAX_PAGE_SIZE + "건까지 본다");
		};
	}

	@GetMapping("/api/admin/evaluations/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 평가다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EvaluationDetailResponse evaluationDetail(@PathVariable long id) {
		EvaluationId evaluation = AdminIds.evaluation(id);
		return this.review.detail(evaluation)
			.map(detail -> EvaluationDetailResponse.of(detail, this.articles.detail(detail.articleId()).orElse(null)))
			.orElseThrow(() -> AdminIds.unknownEvaluation(id));
	}

	@GetMapping("/api/admin/evaluation-samples")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM + ", 또는 표본 크기 · 씨앗이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 태그 체계 버전이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EvaluationSampleResponse evaluationSample(@Parameter(description = "태그 체계 버전 ID") @RequestParam long version,
			@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "씨앗 — 같은 씨앗이면 같은 표본") @RequestParam long seed,
			@Parameter(description = "표본 크기 — 없으면 " + EvaluationReview.DEFAULT_SAMPLE_SIZE) @RequestParam(required = false) @Nullable Integer size) {
		TagSetId id = AdminIds.tagSet(version);
		EvaluationPeriod period = AdminPeriods.of(from, to, zone);
		int chosen = (size != null) ? size : EvaluationReview.DEFAULT_SAMPLE_SIZE;
		return switch (this.review.sample(id, period, seed, chosen)) {
			case EvaluationSampleResult.Sampled sampled ->
				EvaluationSampleResponse.of(sampled.sample(), rows(sampled.sample().rows()));
			case EvaluationSampleResult.UnknownVersion unknown -> throw AdminIds.unknownTagSet(version);
			case EvaluationSampleResult.SizeOutOfRange outOfRange ->
				throw badRequest("표본은 1건에서 " + EvaluationReview.MAX_SAMPLE_SIZE + "건까지다");
			case EvaluationSampleResult.SeedOutOfRange outOfRange ->
				throw badRequest("씨앗은 0에서 " + EvaluationReview.MAX_SEED + "까지다");
		};
	}

	/** 행마다 기사 정보를 맞붙인다 — 기사 조회 · 피드 조회 한 번씩 */
	private List<EvaluationRowResponse> rows(List<EvaluationRow> rows) {
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles
			.summaries(rows.stream().map(EvaluationRow::articleId).toList());
		return rows.stream().map(row -> EvaluationRowResponse.of(row, summaries.get(row.articleId()))).toList();
	}

	private static @Nullable TagRef tag(@Nullable TagKind kind, @Nullable String key) {
		if ((kind == null) != (key == null)) {
			throw badRequest("tagKind 와 tag 는 함께 준다");
		}
		return (kind == null) ? null : new TagRef(kind, Objects.requireNonNull(key));
	}

	private static @Nullable EvaluationCursor cursor(@Nullable String evaluatedAt, @Nullable Long id) {
		AdminCursor cursor = AdminCursor.of(evaluatedAt, id, "afterEvaluatedAt", "평가");
		return (cursor == null) ? null : new EvaluationCursor(cursor.at(), new EvaluationId(cursor.id()));
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
