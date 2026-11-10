package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineLinkingAdministration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API 의 스토리라인 잇기 채우기 — {@code storyline} 의 공개 운영 API 결과를 옮긴다 · docs/specs/admin.md §3 · admin-R1 ·
 * admin-R3.
 */
@WebRole
@RestController
class AdminStorylineLinkingController {

	private static final String BAD_REQUEST = "요청 본문의 형식이 틀렸거나 채울 기간이 범위 밖이다";

	private final StorylineLinkingAdministration administration;

	AdminStorylineLinkingController(StorylineLinkingAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@PostMapping("/api/admin/storyline-linking-fills")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = BAD_REQUEST,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineLinkingFillResponse fillStorylineLinking(@Valid @RequestBody StorylineLinkingFillRequest body) {
		return StorylineLinkingFillResponse.of(this.administration.fill(body.days()));
	}

}
