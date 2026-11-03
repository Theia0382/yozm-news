package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.evaluation.EvaluationAdministration;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TopicCoverage;
import net.yozmnews.evaluation.TopicCoverageResult;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 평가 현황(버전 · 기간의 품질 지표), 주제별 보도량, 평가 대기 · 실패 건수. docs/specs/admin.md §3.
 * <p>
 * {@code evaluation} 의 공개 운영 API · 공개 조회를 불러 응답으로 옮기기만 한다. 트랜잭션을 열지 않는다 — 집계의 트랜잭션은 공개 API 가
 * 연다. 기간은 시작일 · 종료일 · 시간대로 받는다({@link AdminPeriods}).
 */
@WebRole
@RestController
class AdminEvaluationController {

	private static final String PERIOD_PROBLEM = "날짜 · 시간대의 형식이 틀렸거나 기간이 범위 밖이다";

	private final EvaluationAdministration administration;

	private final TopicCoverage coverage;

	AdminEvaluationController(EvaluationAdministration administration, TopicCoverage coverage) {
		this.administration = Objects.requireNonNull(administration, "administration");
		this.coverage = Objects.requireNonNull(coverage, "coverage");
	}

	@GetMapping("/api/admin/topic-coverage")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = PERIOD_PROBLEM + ", 또는 활성 태그 체계에 없는 주제다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TopicCoverageResponse topicCoverage(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone,
			@Parameter(description = "고를 주제 키 — 되풀이한다. 없으면 모든 주제") @RequestParam(name = "topic",
					required = false) List<String> topics) {
		EvaluationPeriod period = AdminPeriods.of(from, to, zone);
		return switch (this.coverage.count(period, (topics == null) ? Set.of() : Set.copyOf(topics))) {
			case TopicCoverageResult.Counted counted -> TopicCoverageResponse.of(counted.view());
			case TopicCoverageResult.UnknownTopics unknown -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"활성 태그 체계에 없는 주제다: " + String.join(", ", unknown.keys()));
		};
	}

	@GetMapping("/api/admin/evaluation-overview")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = PERIOD_PROBLEM,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 태그 체계 버전이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	EvaluationOverviewResponse evaluationOverview(@Parameter(description = "태그 체계 버전 ID") @RequestParam long version,
			@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone) {
		TagSetId id = AdminIds.tagSet(version);
		EvaluationPeriod period = AdminPeriods.of(from, to, zone);
		return this.administration.overview(id, period)
			.map(EvaluationOverviewResponse::of)
			.orElseThrow(() -> AdminIds.unknownTagSet(version));
	}

	@GetMapping("/api/admin/evaluation-queue")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	EvaluationQueueResponse evaluationQueue() {
		return EvaluationQueueResponse.of(this.administration.queueCounts());
	}

}
