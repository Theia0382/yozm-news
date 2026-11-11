package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.tag.TagBackfillAdministration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API 의 빠진 태그 채움 — 확인 · 요청 · 목록. {@code tag} 의 공개 운영 API 결과를 옮긴다 · docs/specs/admin.md §3 ·
 * admin-R1 · admin-R3.
 */
@WebRole
@RestController
class AdminTagBackfillController {

	private static final String BAD_REQUEST = "요청 본문의 형식이 틀렸거나 채울 기간이 범위 밖이다";

	private final TagBackfillAdministration administration;

	AdminTagBackfillController(TagBackfillAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@PostMapping("/api/admin/tag-backfill-reviews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = BAD_REQUEST,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TagBackfillReviewResponse review(@Valid @RequestBody TagBackfillRequest body) {
		return TagBackfillReviewResponse.of(this.administration.review(body.days()));
	}

	@PostMapping("/api/admin/tag-backfills")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = BAD_REQUEST,
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TagBackfillResponse request(@Valid @RequestBody TagBackfillRequest body) {
		return TagBackfillResponse.of(this.administration.request(body.days()));
	}

	@GetMapping("/api/admin/tag-backfills")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	TagBackfillListResponse runs() {
		return TagBackfillListResponse.of(this.administration.runs());
	}

}
