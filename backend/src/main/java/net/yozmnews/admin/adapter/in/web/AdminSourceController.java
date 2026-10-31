package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.SourceAdministration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 소스 목록 · 운영자 원문 등록 · 조회 · 수정 · 미리보기, 소스 켜기 · 끄기 · 수집 주기. docs/specs/admin.md §3.
 * <p>
 * 피드 켜기 · 원문 미리보기는 피드를 받으므로 응답까지 걸릴 수 있다 — 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
@ApiResponse(responseCode = "401", description = "운영자 토큰이 없거나 맞지 않는다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
@ApiResponse(responseCode = "403", description = "운영자 기능이 닫혀 있다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
class AdminSourceController {

	/** 프리셋을 참조하는 소스에 운영자 원문을 묻거나 고칠 때의 404 문구 */
	static final String PRESET_SOURCE_HAS_NO_TEXT = "프리셋을 참조하는 소스라 운영자 원문이 없다";

	private final SourceAdministration administration;

	AdminSourceController(SourceAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/sources")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	SourceListResponse sources() {
		return new SourceListResponse(SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES,
				this.administration.sources().stream().map(SourceResponse::of).toList());
	}

	@PostMapping("/api/admin/sources")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	RegistrationResponse register(@Valid @RequestBody ProfileText body) {
		return RegistrationResponse.of(this.administration.registerOperatorProfile(body.profile()));
	}

	@GetMapping("/api/admin/sources/{id}/profile")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 소스이거나 프리셋을 참조하는 소스다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ProfileText profile(@PathVariable long id) {
		return this.administration.operatorProfileText(AdminIds.source(id))
			.map(ProfileText::new)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRESET_SOURCE_HAS_NO_TEXT));
	}

	@PutMapping("/api/admin/sources/{id}/profile")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 소스이거나 프리셋을 참조하는 소스다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	RevisionResponse revise(@PathVariable long id, @Valid @RequestBody ProfileText body) {
		return RevisionResponse.of(this.administration.reviseOperatorProfile(AdminIds.source(id), body.profile()));
	}

	@PostMapping("/api/admin/profile-previews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	PreviewResponse preview(@Valid @RequestBody PreviewRequest body) {
		return PreviewResponse.of(this.administration.previewOperatorProfile(body.profile(), body.feedKey()));
	}

	@PostMapping("/api/admin/sources/{id}/enable")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 소스다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	SourceEnableResponse enable(@PathVariable long id) {
		return SourceEnableResponse.of(this.administration.enableSource(AdminIds.source(id)));
	}

	@PostMapping("/api/admin/sources/{id}/disable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "없는 소스다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	void disable(@PathVariable long id) {
		this.administration.disableSource(AdminIds.source(id));
	}

	@PutMapping("/api/admin/sources/{id}/poll-interval")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "400", description = "수집 주기가 하한보다 짧다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 소스다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "다른 요청이 먼저 바꿨다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	void changePollInterval(@PathVariable long id, @RequestBody PollIntervalRequest body) {
		if (!this.administration.changeSourcePollInterval(AdminIds.source(id), body.minutes())) {
			throw PollIntervals.belowMinimum();
		}
	}

}
