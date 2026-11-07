package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventAdministration;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 기간의 큰 사건, 큰 사건 기준의 지금 값 · 이력 · 미리보기 · 변경. docs/specs/admin.md §3 · admin-R7.
 * <p>
 * {@code event} 의 공개 조회 · 운영 API 를 불러 옮기고, 대표 기사의 정보는 {@link AdminArticles} 가 맞붙인다(admin-R6). 변경은 행위자
 * {@link ChangeActor#OPERATOR} 로 부른다. 기준 · 기간의 범위는 공개 타입 · 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로
 * 옮긴다(admin-R3). 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminBigEventController {

	private final EventDirectory directory;

	private final BigEventAdministration administration;

	private final AdminArticles articles;

	AdminBigEventController(EventDirectory directory, BigEventAdministration administration, ArticleDirectory articles,
			FeedLabels labels) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.administration = Objects.requireNonNull(administration, "administration");
		this.articles = new AdminArticles(articles, labels);
	}

	@GetMapping("/api/admin/big-events")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigEventListResponse bigEvents(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone) {
		EventPeriod period = AdminPeriods.events(from, to, zone);
		BigEventCriteria criteria = this.administration.criteria();
		return BigEventListResponse.of(BigEventCriteriaResponse.of(criteria), rows(this.directory.bigEvents(period)));
	}

	@GetMapping("/api/admin/big-event-criteria")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	BigEventCriteriaStatusResponse criteria() {
		return new BigEventCriteriaStatusResponse(BigEventCriteriaResponse.of(this.administration.criteria()),
				this.administration.changes().stream().map(BigEventCriteriaHistoryResponse::of).toList());
	}

	@PostMapping("/api/admin/big-event-criteria-previews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400",
			description = "요청 본문의 형식이 틀렸거나, 날짜 · 시간대 · 기간 · 큰 사건 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigEventPreviewResponse preview(@Valid @RequestBody BigEventPreviewRequest body) {
		BigEventCriteria proposed = BigEventCriteria.of(body.minSources(), body.minImportance())
			.orElseThrow(BigEventCriteriaProblem::outOfRange);
		EventPeriod period = AdminPeriods.events(body.from(), body.to(), body.zone());
		BigEventCriteria current = this.administration.criteria();
		List<BigEvent> now = this.directory.bigEvents(period);
		List<BigEvent> then = this.administration.preview(proposed, period);
		return new BigEventPreviewResponse(BigEventCriteriaResponse.of(current), BigEventCriteriaResponse.of(proposed),
				rows(now), rows(then));
	}

	@PutMapping("/api/admin/big-event-criteria")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 큰 사건 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigEventCriteriaChangeResponse change(@Valid @RequestBody BigEventCriteriaChangeRequest body) {
		return BigEventCriteriaChangeResponse
			.of(this.administration.change(body.minSources(), body.minImportance(), ChangeActor.OPERATOR));
	}

	/** 큰 사건마다 사건 전체의 대표 기사와 그 정보 — 대표 조회 · 기사 정보 조회 한 번씩 */
	private List<BigEventRowResponse> rows(List<BigEvent> events) {
		Map<EventId, ArticleId> representatives = this.directory
			.representativesOfAll(events.stream().map(BigEvent::id).toList());
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles
			.summaries(List.copyOf(representatives.values()));
		return events.stream().map(event -> {
			ArticleId representative = representatives.get(event.id());
			return BigEventRowResponse.of(event, representative,
					(representative == null) ? null : summaries.get(representative));
		}).toList();
	}

}
