package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineAdministration;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.StorylineDirectory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 기간의 큰 스토리라인과 소속 없는 사건, 큰 판정 기준의 지금 값 · 이력 · 미리보기 · 변경. docs/specs/admin.md §3 · admin-R7.
 * <p>
 * {@code storyline} 의 공개 조회 · 운영 API 를 불러 옮기고, 스토리라인의 대표 사건의 대표 기사와 그 정보는 {@link AdminRepresentatives} 가
 * 맞붙인다(admin-R6). 변경은 행위자 {@link ChangeActor#OPERATOR} 로 부른다. 기준 · 기간의 범위는 공개 타입 · 공개
 * API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R3). 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminBigStorylineController {

	private final StorylineDirectory storylines;

	private final BigStorylineAdministration administration;

	private final AdminRepresentatives representatives;

	AdminBigStorylineController(StorylineDirectory storylines, BigStorylineAdministration administration,
			EventDirectory events, ArticleDirectory articles, FeedLabels labels) {
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.administration = Objects.requireNonNull(administration, "administration");
		this.representatives = new AdminRepresentatives(events, new AdminArticles(articles, labels));
	}

	@GetMapping("/api/admin/big-storylines")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = AdminPeriods.PROBLEM,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigStorylineListResponse bigStorylines(@Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam String from,
			@Parameter(description = "종료일(yyyy-MM-dd, 포함)") @RequestParam String to,
			@Parameter(description = "날짜를 해석할 시간대(IANA ID)") @RequestParam String zone) {
		EventPeriod period = AdminPeriods.events(from, to, zone);
		BigStorylineCriteria criteria = this.administration.criteria();
		List<BigStoryline> big = this.storylines.bigStorylines(period);
		return BigStorylineListResponse.of(BigStorylineCriteriaResponse.of(criteria), rows(big, representatives(big)),
				UnlinkedEventsResponse.of(this.storylines.unlinked(period)));
	}

	@GetMapping("/api/admin/big-storyline-criteria")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	BigStorylineCriteriaStatusResponse criteria() {
		return new BigStorylineCriteriaStatusResponse(BigStorylineCriteriaResponse.of(this.administration.criteria()),
				this.administration.changes().stream().map(BigStorylineCriteriaHistoryResponse::of).toList());
	}

	@PostMapping("/api/admin/big-storyline-criteria-previews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400",
			description = "요청 본문의 형식이 틀렸거나, 날짜 · 시간대 · 기간 · 큰 판정 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigStorylinePreviewResponse preview(@Valid @RequestBody BigStorylinePreviewRequest body) {
		BigStorylineCriteria proposed = BigStorylineCriteria.of(body.minIntensity())
			.orElseThrow(BigStorylineCriteriaProblem::outOfRange);
		EventPeriod period = AdminPeriods.events(body.from(), body.to(), body.zone());
		BigStorylineCriteria current = this.administration.criteria();
		List<BigStoryline> now = this.storylines.bigStorylines(period);
		List<BigStoryline> then = this.administration.preview(proposed, period);
		// 두 목록의 대표를 한 번에 맞붙인다
		Map<EventId, AdminRepresentatives.Representative> representatives = representatives(
				Stream.concat(now.stream(), then.stream()).toList());
		return new BigStorylinePreviewResponse(BigStorylineCriteriaResponse.of(current),
				BigStorylineCriteriaResponse.of(proposed), rows(now, representatives), rows(then, representatives));
	}

	@PutMapping("/api/admin/big-storyline-criteria")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 큰 판정 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	BigStorylineCriteriaChangeResponse change(@Valid @RequestBody BigStorylineCriteriaChangeRequest body) {
		return BigStorylineCriteriaChangeResponse
			.of(this.administration.change(body.minIntensity(), ChangeActor.OPERATOR));
	}

	/** 큰 스토리라인마다 대표 사건의 대표 기사와 그 정보 — 대표 조회 · 기사 정보 조회 한 번씩 */
	private Map<EventId, AdminRepresentatives.Representative> representatives(List<BigStoryline> storylines) {
		return this.representatives.of(storylines.stream().map(big -> big.storyline().representativeEvent()).toList());
	}

	private static List<BigStorylineRowResponse> rows(List<BigStoryline> storylines,
			Map<EventId, AdminRepresentatives.Representative> representatives) {
		return storylines.stream()
			.map(big -> BigStorylineRowResponse.of(big, representatives.getOrDefault(big.storyline().representativeEvent(),
					AdminRepresentatives.Representative.NONE)))
			.toList();
	}

}
