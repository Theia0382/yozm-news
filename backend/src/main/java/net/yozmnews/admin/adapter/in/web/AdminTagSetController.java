package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 태그 체계 버전 목록 · 버전 원문, 운영자 원문의 확인 · 활성화. docs/specs/admin.md §3.
 * <p>
 * 트랜잭션을 열지 않는다 — 활성화의 트랜잭션은 {@code evaluation} 의 공개 운영 API 가 연다. 운영자 요청은 행위자
 * {@link ChangeActor#OPERATOR} 로 부른다.
 */
@WebRole
@RestController
@ApiResponse(responseCode = "401", description = "운영자 토큰이 없거나 맞지 않는다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
@ApiResponse(responseCode = "403", description = "운영자 기능이 닫혀 있다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
class AdminTagSetController {

	private final TagSetAdministration administration;

	AdminTagSetController(TagSetAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/tag-sets")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	TagSetListResponse versions() {
		return TagSetListResponse.of(this.administration.versions());
	}

	@GetMapping("/api/admin/tag-sets/{id}/text")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 태그 체계 버전이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TagSetText text(@PathVariable long id) {
		return this.administration.versionText(AdminIds.tagSet(id))
			.map(TagSetText::new)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 태그 체계 버전: " + id));
	}

	@PostMapping("/api/admin/tag-set-reviews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 재평가 기간이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TagSetReviewResponse review(@Valid @RequestBody TagSetRequest body) {
		return TagSetReviewResponse.of(this.administration.review(body.text(), body.days()));
	}

	@PostMapping("/api/admin/tag-sets")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 재평가 기간이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	TagSetActivationResponse activate(@Valid @RequestBody TagSetRequest body) {
		return TagSetActivationResponse
			.of(this.administration.activate(body.text(), body.days(), ChangeActor.OPERATOR));
	}

}
