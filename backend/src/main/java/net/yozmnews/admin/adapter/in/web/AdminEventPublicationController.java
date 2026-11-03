package net.yozmnews.admin.adapter.in.web;

import java.util.HashSet;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.BatchResult;
import net.yozmnews.platform.EventPublicationAdministration;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 남겨진 이벤트 발행의 목록 · 다시 처리 · 폐기. docs/specs/admin.md §3.
 * <p>
 * {@code platform} 의 공개 운영 API 를 부르고 결과를 응답 레코드로 옮긴다. 한 번에 받는 발행 수의 상한은 공개 API 가 판정한다. 트랜잭션을 열지
 * 않는다.
 */
@WebRole
@RestController
class AdminEventPublicationController {

	private final EventPublicationAdministration administration;

	AdminEventPublicationController(EventPublicationAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/event-publications")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	StrandedPublicationListResponse strandedPublications() {
		return StrandedPublicationListResponse.of(this.administration.strandedPublications());
	}

	@PostMapping("/api/admin/event-publications/resubmission")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "본문 형식이 틀렸거나 한 번에 받는 발행 수를 넘었다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ResubmissionListResponse resubmit(@Valid @RequestBody EventPublicationIdsRequest body) {
		return switch (this.administration.resubmit(new HashSet<>(body.ids()))) {
			case BatchResult.Processed(var results) -> ResubmissionListResponse.of(results);
			case BatchResult.OverLimit(int limit) -> throw overLimit(limit);
		};
	}

	@PostMapping("/api/admin/event-publications/discard")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "본문 형식이 틀렸거나 한 번에 받는 발행 수를 넘었다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	DiscardListResponse discard(@Valid @RequestBody EventPublicationIdsRequest body) {
		return switch (this.administration.discard(new HashSet<>(body.ids()))) {
			case BatchResult.Processed(var results) -> DiscardListResponse.of(results);
			case BatchResult.OverLimit(int limit) -> throw overLimit(limit);
		};
	}

	private static ResponseStatusException overLimit(int limit) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "한 번에 " + limit + "건까지 처리한다");
	}

}
