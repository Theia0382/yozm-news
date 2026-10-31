package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.SourceAdministration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 피드 켜기 · 끄기 · 수집 주기. docs/specs/admin.md §3.
 * <p>
 * 꺼진 피드를 켜면 한 번 받아 미리보므로 같은 언론사의 앞선 요청을 기다린 만큼 응답이 늦다 — 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
@ApiResponse(responseCode = "401", description = "운영자 토큰이 없거나 맞지 않는다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
@ApiResponse(responseCode = "403", description = "운영자 기능이 닫혀 있다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
class AdminFeedController {

	private final SourceAdministration administration;

	AdminFeedController(SourceAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@PostMapping("/api/admin/feeds/{id}/enable")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 피드다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	FeedEnableResponse enable(@PathVariable long id) {
		return FeedEnableResponse.of(this.administration.enableFeed(AdminIds.feed(id)));
	}

	@PostMapping("/api/admin/feeds/{id}/disable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "없는 피드다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	void disable(@PathVariable long id) {
		this.administration.disableFeed(AdminIds.feed(id));
	}

	@PutMapping("/api/admin/feeds/{id}/poll-interval")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "400", description = "수집 주기가 하한보다 짧다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 피드다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	void changePollInterval(@PathVariable long id, @RequestBody PollIntervalRequest body) {
		if (!this.administration.changeFeedPollInterval(AdminIds.feed(id), body.minutes())) {
			throw PollIntervals.belowMinimum();
		}
	}

}
